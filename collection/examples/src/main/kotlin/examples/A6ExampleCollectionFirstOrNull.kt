package gog.my_project.collection.examples

import examples.UserModel
import gog.my_project.collection.Collection

class A6ExampleCollectionFirstOrNull {
    fun run() {
        val users = Collection<UserModel>()
        users.add(UserModel(1, "Ada", "ada@example.com"))
        users.add(UserModel(2, "Grace", null))
        users.add(UserModel(3, "Alan", "alan@example.com"))

        val firstUserWithANameStartingWithA = users.firstOrNull { user -> user.name.startsWith("A") }

        println("First matching user: ${firstUserWithANameStartingWithA?.name}")
    }
}
