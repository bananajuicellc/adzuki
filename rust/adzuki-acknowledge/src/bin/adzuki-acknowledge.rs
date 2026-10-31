use clap::Parser;
use crossterm::event::{self, Event, KeyCode, MouseButton, MouseEventKind};
use ratatui::{
    DefaultTerminal, Frame,
    layout::{Constraint, Direction, Layout, Rect},
    style::{Color, Style},
};
use ratatui_interact::{
    components::{
        ScrollableContent, ScrollableContentState, handle_scrollable_content_key,
        handle_scrollable_content_mouse,
    },
    events::{has_ctrl, is_ctrl_a},
    state::FocusManager,
    utils::{copy_to_clipboard, disable_mouse_capture, enable_mouse_capture},
};
use rustledger_loader::Loader;
use std::{io, path::PathBuf};

// 1. Identify interactive widgets
#[derive(Clone, PartialEq, Eq, Hash)]
enum WidgetId {
    MainTextBox,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
struct TextPos {
    line: usize,
    col: usize,
}

// 2. Define App state
struct App {
    should_quit: bool,
    focus_manager: FocusManager<WidgetId>,
    text_box_state: ScrollableContentState,
    text_box_area: Rect,
    selection: Option<(TextPos, TextPos)>,
}

impl App {
    fn new() -> Self {
        let mut focus_manager = FocusManager::new();
        focus_manager.register(WidgetId::MainTextBox);

        let mut text_box_state = ScrollableContentState::empty();
        text_box_state.set_focused(focus_manager.is_focused(&WidgetId::MainTextBox));

        Self {
            should_quit: false,
            focus_manager,
            text_box_state,
            text_box_area: Rect::default(),
            selection: None,
        }
    }

    fn inner_area(&self) -> Rect {
        ScrollableContent::new(&self.text_box_state).inner_area(self.text_box_area)
    }

    fn mouse_to_text_pos(&self, column: u16, row: u16) -> Option<TextPos> {
        let line_count = self.text_box_state.line_count();
        if line_count == 0 {
            return None;
        }
        let inner = self.inner_area();
        if inner.width == 0 || inner.height == 0 {
            return None;
        }

        let clamped_row = row.clamp(inner.y, inner.y + inner.height.saturating_sub(1));
        let clamped_col = column.clamp(inner.x, inner.x + inner.width.saturating_sub(1));

        let line = (self.text_box_state.scroll_offset() + (clamped_row - inner.y) as usize)
            .min(line_count.saturating_sub(1));
        let line_len = self
            .text_box_state
            .lines()
            .get(line)
            .map_or(0, |l| l.chars().count());
        let col = ((clamped_col - inner.x) as usize).min(line_len);

        Some(TextPos { line, col })
    }

    fn select_all(&mut self) {
        let lines = self.text_box_state.lines();
        if let Some(last_line) = lines.last() {
            let start = TextPos { line: 0, col: 0 };
            let end = TextPos {
                line: lines.len() - 1,
                col: last_line.chars().count(),
            };
            self.selection = Some((start, end));
        }
    }

    fn normalized_selection(&self) -> Option<(TextPos, TextPos)> {
        let (a, b) = self.selection?;
        if a == b {
            None
        } else if a < b {
            Some((a, b))
        } else {
            Some((b, a))
        }
    }

    fn selected_text(&self) -> Option<String> {
        let (start, end) = self.normalized_selection()?;
        let lines = self.text_box_state.lines();
        if start.line >= lines.len() {
            return None;
        }
        let end_line = end.line.min(lines.len().saturating_sub(1));

        if start.line == end_line {
            let line: String = lines[start.line]
                .chars()
                .skip(start.col)
                .take(end.col.saturating_sub(start.col))
                .collect();
            return Some(line);
        }

        let mut selected_lines = Vec::with_capacity(end_line - start.line + 1);
        selected_lines.push(lines[start.line].chars().skip(start.col).collect::<String>());
        for line in &lines[start.line + 1..end_line] {
            selected_lines.push(line.clone());
        }
        selected_lines.push(lines[end_line].chars().take(end.col).collect::<String>());

        Some(selected_lines.join("\n"))
    }

    // 3. Drive the core application loop
    fn run(mut self, mut terminal: DefaultTerminal) -> color_eyre::Result<()> {
        // TODO: load in the background and display a spinner.
        let cli = Cli::parse();
        let result = Loader::new().load(&cli.beancount_path)?;
        let lines = result
            .directives
            .iter()
            .flat_map(|d| d.to_string().lines().map(String::from).collect::<Vec<_>>())
            .collect();
        self.text_box_state.set_lines(lines);

        while !self.should_quit {
            // Render Frame
            terminal.draw(|frame| self.draw(frame))?;

            let inner = self.inner_area();
            let visible_height = inner.height as usize;

            // Read event
            match event::read()? {
                // Key Handlers
                Event::Key(key) if key.kind == event::KeyEventKind::Press => {
                    if key.code == KeyCode::Char('q') && !has_ctrl(&key) {
                        self.should_quit = true;
                    } else if is_ctrl_a(&key) {
                        self.select_all();
                    } else if key.code == KeyCode::Char('c') && has_ctrl(&key) {
                        if let Some(text) = self.selected_text() {
                            let _ = copy_to_clipboard(&text);
                        }
                    } else if key.code == KeyCode::Esc {
                        self.selection = None;
                    } else {
                        handle_scrollable_content_key(
                            &mut self.text_box_state,
                            &key,
                            visible_height,
                        );
                    }
                }

                // Mouse Interceptor Pipelines
                Event::Mouse(mouse) => {
                    match mouse.kind {
                        MouseEventKind::Down(MouseButton::Left) => {
                            // Assign component focus on click
                            self.focus_manager.set(WidgetId::MainTextBox);
                            self.text_box_state.set_focused(true);
                            if let Some(pos) = self.mouse_to_text_pos(mouse.column, mouse.row) {
                                self.selection = Some((pos, pos));
                            }
                        }
                        MouseEventKind::Drag(MouseButton::Left) => {
                            if inner.height > 0 {
                                if mouse.row < inner.y {
                                    self.text_box_state.scroll_up(1);
                                } else if mouse.row >= inner.y + inner.height {
                                    self.text_box_state.scroll_down(1, visible_height);
                                }
                            }
                            if let (Some((anchor, _)), Some(pos)) =
                                (self.selection, self.mouse_to_text_pos(mouse.column, mouse.row))
                            {
                                self.selection = Some((anchor, pos));
                            }
                        }
                        _ => {
                            handle_scrollable_content_mouse(
                                &mut self.text_box_state,
                                &mouse,
                                self.text_box_area,
                                visible_height,
                            );
                        }
                    }
                }
                _ => {}
            }
        }
        Ok(())
    }

    // 4. UI Layout Definition
    fn draw(&mut self, frame: &mut Frame) {
        let chunks = Layout::default()
            .direction(Direction::Vertical)
            .constraints([Constraint::Min(0), Constraint::Length(1)])
            .split(frame.area());

        self.text_box_area = chunks[0];
        self.text_box_state
            .set_focused(self.focus_manager.is_focused(&WidgetId::MainTextBox));

        // Instantiate and mount the scrollable widget
        let text_box =
            ScrollableContent::new(&self.text_box_state).title("Scrollable Content Container");
        let inner = text_box.inner_area(chunks[0]);

        frame.render_widget(text_box, chunks[0]);

        // Highlight selected text if any
        if let Some((start, end)) = self.normalized_selection() {
            let scroll_offset = self.text_box_state.scroll_offset();
            let visible_height = inner.height as usize;
            let lines = self.text_box_state.lines();
            let highlight_style = Style::default().fg(Color::Black).bg(Color::Cyan);
            let buf = frame.buffer_mut();

            for row_offset in 0..visible_height {
                let line_idx = scroll_offset + row_offset;
                if line_idx < start.line || line_idx > end.line || line_idx >= lines.len() {
                    continue;
                }

                let line_len = lines[line_idx].chars().count();
                let col_start = if line_idx == start.line {
                    start.col
                } else {
                    0
                };
                let col_end = if line_idx == end.line {
                    end.col
                } else {
                    line_len
                };

                for col in col_start..col_end.min(inner.width as usize) {
                    let x = inner.x + col as u16;
                    let y = inner.y + row_offset as u16;
                    if let Some(cell) = buf.cell_mut((x, y)) {
                        cell.set_style(highlight_style);
                    }
                }
            }
        }
    }
}

#[derive(Parser)]
#[command(version, about, long_about = None)]
struct Cli {
    beancount_path: PathBuf,
}

fn main() -> color_eyre::Result<()> {
    color_eyre::install()?;
    let terminal = ratatui::init();
    let _ = enable_mouse_capture(&mut io::stdout());

    let app = App::new();
    let app_result = app.run(terminal);

    // Standard cleanup routines (restores terminal raw mode & cursor state)
    let _ = disable_mouse_capture(&mut io::stdout());
    ratatui::restore();

    app_result
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_select_all_and_selected_text() {
        let mut app = App::new();
        app.text_box_state.set_lines(vec![
            "2026-01-01 open Assets:Checking USD".to_string(),
            "2026-01-02 * \"Test\"".to_string(),
            "  Assets:Checking 10.00 USD".to_string(),
        ]);

        assert_eq!(app.selected_text(), None);

        app.select_all();
        assert_eq!(
            app.selected_text().as_deref(),
            Some(
                "2026-01-01 open Assets:Checking USD\n2026-01-02 * \"Test\"\n  Assets:Checking 10.00 USD"
            )
        );
    }

    #[test]
    fn test_mouse_drag_selection() {
        let mut app = App::new();
        app.text_box_area = Rect::new(0, 0, 40, 10);
        app.text_box_state.set_lines(vec![
            "Hello world".to_string(),
            "Second line".to_string(),
        ]);

        // Inner area starts at (1, 1) due to borders
        let start = app.mouse_to_text_pos(1, 1).unwrap();
        let end = app.mouse_to_text_pos(7, 2).unwrap();
        app.selection = Some((start, end));

        assert_eq!(app.selected_text().as_deref(), Some("Hello world\nSecond"));
    }
}
