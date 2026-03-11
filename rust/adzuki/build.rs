use std::env;
use std::path::PathBuf;

fn main() {
    // Both lelwel files need to be placed in OUT_DIR, but build() automatically outputs to `generated.rs`.
    // Since build() hardcodes `generated.rs` or uses the output dir directly, we need to use compile.

    // We will rename `beancount_parser.rs` to just include the output of a specific target directory.
    let out_dir = env::var("OUT_DIR").unwrap();
    let res_md = lelwel::compile("src/markdown.llw", &out_dir, false, false, 0, false, false).unwrap();
    if !res_md { std::process::exit(1); }
    println!("cargo:rerun-if-changed=src/markdown.llw");

    // Because lelwel forces output file name to `generated.rs`, we rename it.
    let generated = PathBuf::from(&out_dir).join("generated.rs");
    let md_target = PathBuf::from(&out_dir).join("markdown_generated.rs");
    if generated.exists() {
        std::fs::rename(&generated, &md_target).unwrap();
    }

    let res_bc = lelwel::compile("src/beancount.llw", &out_dir, false, false, 0, false, false).unwrap();
    if !res_bc { std::process::exit(1); }
    println!("cargo:rerun-if-changed=src/beancount.llw");

    let bc_target = PathBuf::from(&out_dir).join("beancount_generated.rs");
    if generated.exists() {
        std::fs::rename(&generated, &bc_target).unwrap();
    }
}
