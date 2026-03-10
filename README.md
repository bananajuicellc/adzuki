# Adzuki - Plain Text Accounting

A literate Beancount implementation in Rust.

## Motivation

As the treasurer for a monthly meeting of the Religious Society of Friends (Quaker church), my primary goal is to create a cross-platform, plain text accounting application to collaborate on the Meeting's finances with my eventual successor and potentially with a finance committee if the meeting is led to form one.

My goal in using a plain text approach is to ensure that financial records remain accessible, verifiable, and easy to hand off to future treasurers and committee members.

## Literate Programming

To further aid in readability and usability, this project embraces [Literate Programming](https://en.wikipedia.org/wiki/Literate_programming), a paradigm introduced by Donald Knuth in his book of the same name. By combining Beancount syntax with Markdown, committee members and successive treasurers can maintain accounting records interwoven with human-readable explanations and documentation. This makes it significantly easier to understand the context behind financial transactions and make use of these tools.

## Roadmap

In addition to the core application, the long-term vision for `adzuki` includes providing library packages that can be used to programmatically generate reports. Planned features include:

- A cross-platform application for managing literate Beancount files.
- A Python wrapper that mimics the report generating capabilities of the original `beancount` package, allowing for seamless integration with existing tools and scripts.

## Why the name "adzuki"?

Adzuki beans are a rust-colored bean, commonly used in both sweet and savory Japanese dishes. i happen to love Japanese food and the fact that [Beancount](https://github.com/beancount/beancount) and related projects such as [limabean](https://github.com/tesujimath/limabean) and [turbobean](https://github.com/themoritz/turbobean) use bean-related names.
