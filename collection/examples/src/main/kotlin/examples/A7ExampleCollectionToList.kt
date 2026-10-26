package gog.my_project.collection.examples

import examples.UserModel
import gog.my_project.collection.Collection

class A7ExampleCollectionToList {
    fun run() {
        val users = Collection<UserModel>()
        users.add(UserModel(1, "Ada", "ada@example.com"))
        users.add(UserModel(2, "Grace", null))

        val userList: List<UserModel> = users.toList()

        userList.forEach { user ->
            println("id: ${user.id}, name: ${user.name}, email: ${user.email}")
        }
    }
}
