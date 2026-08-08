use crux_core::{
    App, Command,
    macros::effect,
    render::{RenderOperation, render},
};
use serde::{Deserialize, Serialize};

#[derive(Default)]
pub struct Counter;

#[derive(Serialize, Deserialize, Clone, Debug)]
pub enum Event {
    Increment,
    Decrement,
    Reset,
}

#[derive(Default)]
pub struct Model {
    count: isize,
}

#[derive(Serialize, Deserialize, Clone, Default)]
pub struct ViewModel {
    pub count: String,
}

#[effect]
#[derive(Debug)]
pub enum Effect {
    Render(RenderOperation),
}

impl App for Counter {
    type Event = Event;
    type Model = Model;
    type ViewModel = ViewModel;
    type Effect = Effect;

    fn update(&self, event: Event, model: &mut Model) -> Command<Effect, Event> {
        match event {
            Event::Increment => model.count += 1,
            Event::Decrement => model.count -= 1,
            Event::Reset => model.count = 0,
        }

        render()
    }

    fn view(&self, model: &Model) -> ViewModel {
        ViewModel {
            count: format!("Count is: {}", model.count),
        }
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn renders() {
        let app = Counter;
        let mut model = Model::default();

        // Check update asked us to `Render`, and only that
        app.update(Event::Reset, &mut model).expect_only_render();
    }
}
