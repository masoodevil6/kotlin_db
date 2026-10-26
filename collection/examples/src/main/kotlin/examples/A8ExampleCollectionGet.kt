package gog.my_project.collection.examples

import examples.UserModel
import gog.my_project.collection.Collection

class A8ExampleCollectionGet {
    fun run() {
        val users = Collection<UserModel>()
        users.add(UserModel(1, "Ada", "ada@example.com"))
        users.add(UserModel(2, "Grace", null))

        val firstUserName = users[0].name

        println("First user name: $firstUserName")
    }
}
