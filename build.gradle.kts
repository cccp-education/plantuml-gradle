plugins { id("education.cccp.plantuml").version("0.0.0") }
plantuml { configPath = "plantuml-context.yml".run(::file).absolutePath }
