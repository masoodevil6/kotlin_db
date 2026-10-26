package gog.my_project.collection.examples

import examples.UserModel
import gog.my_project.collection.Collection

class A3ExampleCollectionFilter {
    fun run() {
        val users = Collection<UserModel>()
        users.add(UserModel(1, "Ada", "ada@example.com"))
        users.add(UserModel(2, "Grace", null))
        users.add(UserModel(3, "Alan", "alan@example.com"))

        val usersWithAName = users.filter { it.name.startsWith("A") }

        usersWithAName.forEach { user ->
            println("id: ${user.id}, name: ${user.name}, email: ${user.email}")
        }
    }
}
