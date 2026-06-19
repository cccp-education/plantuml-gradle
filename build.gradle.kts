plugins { id("education.cccp.plantuml") }
plantuml { configPath = "plantuml-context.yml".run(::file).absolutePath }
