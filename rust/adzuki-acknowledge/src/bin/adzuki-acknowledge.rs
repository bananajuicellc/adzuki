use adzuki_acknowledge::{Acknowledgements, email};
use clap::Parser;
use crossterm::event::{self, Event, KeyCode, MouseButton, MouseEventKind};
use ratatui::{
    DefaultTerminal, Frame,
    layout::{Constraint, Direction, Layout, Rect},
    style::{Color, Modifier, Style},
    widgets::{Block, Borders, List, ListItem, ListState, Paragraph},
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
use std::{collections::HashMap, io, path::PathBuf};

const EMAIL_SUBJECT: &str = "Contribution Acknowledgement from Northside Friends Meeting";

fn wrap_text(text: &str, max_width: usize) -> Vec<String> {
    if max_width == 0 {
        return text.lines().map(String::from).collect();
    }
    let mut wrapped = Vec::new();
    for line in text.lines() {
        if line.is_empty() {
            wrapped.push(String::new());
            continue;
        }
        let mut current = String::new();
        let mut current_len = 0;
        for word in line.split_whitespace() {
            let word_len = word.chars().count();
            if current.is_empty() {
                current.push_str(word);
                current_len = word_len;
            } else if current_len + 1 + word_len <= max_width {
                current.push(' ');
                current.push_str(word);
                current_len += 1 + word_len;
            } else {
                wrapped.push(current);
                current = word.to_string();
                current_len = word_len;
            }
        }
        if !current.is_empty() {
            wrapped.push(current);
        } else {
            wrapped.push(String::new());
        }
    }
    wrapped
}

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
    acknowledgements: Acknowledgements,
    accounts: Vec<String>,
    list_state: ListState,
    text_box_state: ScrollableContentState,
    text_box_area: Rect,
    last_wrap_width: usize,
    selection: Option<(TextPos, TextPos)>,
}

impl App {
    fn new() -> Self {
        Self::with_acknowledgements(Acknowledgements {
            acknowledgements: HashMap::new(),
        })
    }

    fn with_acknowledgements(acknowledgements: Acknowledgements) -> Self {
        let mut focus_manager = FocusManager::new();
        focus_manager.register(WidgetId::MainTextBox);

        let mut text_box_state = ScrollableContentState::empty();
        text_box_state.set_focused(focus_manager.is_focused(&WidgetId::MainTextBox));

        let mut accounts: Vec<String> = acknowledgements.acknowledgements.keys().cloned().collect();
        accounts.sort();

        let mut list_state = ListState::default();
        if !accounts.is_empty() {
            list_state.select(Some(0));
        }

        let mut app = Self {
            should_quit: false,
            focus_manager,
            acknowledgements,
            accounts,
            list_state,
            text_box_state,
            text_box_area: Rect::default(),
            last_wrap_width: 0,
            selection: None,
        };
        app.update_current_email();
        app
    }

    fn selected_acknowledgement(&self) -> Option<&adzuki_acknowledge::Acknowledgement> {
        let idx = self.list_state.selected()?;
        let account = self.accounts.get(idx)?;
        self.acknowledgements.acknowledgements.get(account)
    }

    fn update_current_email(&mut self) {
        self.selection = None;
        let width = self.inner_area().width as usize;
        self.last_wrap_width = width;
        if let Some(ack) = self.selected_acknowledgement() {
            let rendered = crate::email::render_email(ack);
            self.text_box_state.set_lines(wrap_text(&rendered, width));
            let new_width = self.inner_area().width as usize;
            if new_width != width && new_width > 0 {
                self.last_wrap_width = new_width;
                self.text_box_state
                    .set_lines(wrap_text(&rendered, new_width));
            }
        } else {
            self.text_box_state.set_lines(Vec::new());
        }
    }

    fn next_account(&mut self) {
        if self.accounts.is_empty() {
            return;
        }
        let next = match self.list_state.selected() {
            Some(i) => (i + 1).min(self.accounts.len() - 1),
            None => 0,
        };
        if self.list_state.selected() != Some(next) {
            self.list_state.select(Some(next));
            self.update_current_email();
        }
    }

    fn previous_account(&mut self) {
        if self.accounts.is_empty() {
            return;
        }
        let prev = match self.list_state.selected() {
            Some(i) => i.saturating_sub(1),
            None => 0,
        };
        if self.list_state.selected() != Some(prev) {
            self.list_state.select(Some(prev));
            self.update_current_email();
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
        let acknowledgements =
            crate::Acknowledgements::from_beancount(cli.beancount_path).map_err(|e| {
                color_eyre::eyre::eyre!(e)
            })?;
        self = Self::with_acknowledgements(acknowledgements);

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
                    } else if matches!(key.code, KeyCode::Char('n') | KeyCode::Char('j'))
                        && !has_ctrl(&key)
                    {
                        self.next_account();
                    } else if matches!(key.code, KeyCode::Char('p') | KeyCode::Char('k'))
                        && !has_ctrl(&key)
                    {
                        self.previous_account();
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
        let columns = Layout::default()
            .direction(Direction::Horizontal)
            .constraints([Constraint::Percentage(30), Constraint::Percentage(70)])
            .split(frame.area());

        let items: Vec<ListItem> = self
            .accounts
            .iter()
            .map(|account| ListItem::new(account.as_str()))
            .collect();
        let list = List::new(items)
            .block(Block::default().borders(Borders::ALL).title("Accounts"))
            .highlight_style(
                Style::default()
                    .fg(Color::Black)
                    .bg(Color::Yellow)
                    .add_modifier(Modifier::BOLD),
            )
            .highlight_symbol("> ");
        frame.render_stateful_widget(list, columns[0], &mut self.list_state);

        let right_chunks = Layout::default()
            .direction(Direction::Vertical)
            .constraints([Constraint::Length(4), Constraint::Min(0)])
            .split(columns[1]);

        let email_addr = self
            .selected_acknowledgement()
            .and_then(|ack| ack.email.as_deref())
            .unwrap_or("");
        let header_text = format!("To: {}\nSubject: {}", email_addr, EMAIL_SUBJECT);
        let header_box = Paragraph::new(header_text)
            .block(Block::default().borders(Borders::ALL).title("Email Header"));
        frame.render_widget(header_box, right_chunks[0]);

        self.text_box_area = right_chunks[1];
        if self.inner_area().width as usize != self.last_wrap_width {
            self.update_current_email();
        }
        self.text_box_state
            .set_focused(self.focus_manager.is_focused(&WidgetId::MainTextBox));

        // Instantiate and mount the scrollable widget
        let text_box = ScrollableContent::new(&self.text_box_state).title("Email Body");
        let inner = text_box.inner_area(right_chunks[1]);

        frame.render_widget(text_box, right_chunks[1]);

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

    fn get_test_data_path(filename: &str) -> PathBuf {
        let mut path = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
        path.push("tests");
        path.push("data");
        path.push(filename);
        path
    }

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

    #[test]
    fn test_account_navigation_and_email_rendering() {
        let acks = crate::Acknowledgements::from_beancount(get_test_data_path("standalone.beancount"))
            .expect("should load standalone.beancount");
        let mut app = App::with_acknowledgements(acks);

        assert_eq!(
            app.accounts,
            vec![
                "Income:Donor:KowalskiJan",
                "Income:Donor:PérezJuan",
                "Income:Donor:SmithJane",
            ]
        );
        assert_eq!(app.list_state.selected(), Some(0));
        assert!(app.text_box_state.lines()[0].contains("Income:Donor:KowalskiJan"));

        app.next_account();
        assert_eq!(app.list_state.selected(), Some(1));
        assert!(app.text_box_state.lines()[0].contains("Juan Pérez"));

        app.next_account();
        assert_eq!(app.list_state.selected(), Some(2));
        assert!(app.text_box_state.lines()[0].contains("Income:Donor:SmithJane"));

        // Clamped at the end
        app.next_account();
        assert_eq!(app.list_state.selected(), Some(2));

        app.previous_account();
        assert_eq!(app.list_state.selected(), Some(1));
    }

    #[test]
    fn test_wrap_text() {
        let text = "Greetings Friend,\n\nThis letter gratefully acknowledges receipt of your contribution.";
        let wrapped = wrap_text(text, 25);
        assert_eq!(
            wrapped,
            vec![
                "Greetings Friend,",
                "",
                "This letter gratefully",
                "acknowledges receipt of",
                "your contribution.",
            ]
        );
    }
}
