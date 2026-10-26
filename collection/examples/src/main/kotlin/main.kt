package gog.my_project.collection.examples

fun main() {
    if (configuredRunOption("collection.example.run.add", true)) {
        println("=== add ===")
        A1ExampleCollectionAdd().run()
    }

    if (configuredRunOption("collection.example.run.foreach", true)) {
        println("=== forEach ===")
        A2ExampleCollectionForEach().run()
    }

    if (configuredRunOption("collection.example.run.filter", true)) {
        println("=== filter ===")
        A3ExampleCollectionFilter().run()
    }

    if (configuredRunOption("collection.example.run.size", true)) {
        println("=== size ===")
        A4ExampleCollectionSize().run()
    }

    if (configuredRunOption("collection.example.run.map", true)) {
        println("=== map ===")
        A5ExampleCollectionMap().run()
    }

    if (configuredRunOption("collection.example.run.first-or-null", true)) {
        println("=== firstOrNull ===")
        A6ExampleCollectionFirstOrNull().run()
    }

    if (configuredRunOption("collection.example.run.to-list", true)) {
        println("=== toList ===")
        A7ExampleCollectionToList().run()
    }

    if (configuredRunOption("collection.example.run.get", true)) {
        println("=== get ===")
        A8ExampleCollectionGet().run()
    }
}

private fun configuredRunOption(name: String, default: Boolean): Boolean {
    val value = System.getProperty(name) ?: return default
    return value.toBooleanStrictOrNull()
        ?: throw IllegalArgumentException("System property '$name' must be 'true' or 'false'")
}
