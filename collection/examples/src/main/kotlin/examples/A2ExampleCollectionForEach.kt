package gog.my_project.collection.examples

import examples.UserModel
import gog.my_project.collection.Collection

class A2ExampleCollectionForEach {
    fun run() {
        val users = Collection<UserModel>()
        users.add(UserModel(1, "Ada", "ada@example.com"))
        users.add(UserModel(2, "Grace", null))

        users.forEach { user ->
            println("id: ${user.id}, name: ${user.name}, email: ${user.email}")
        }
    }
}
