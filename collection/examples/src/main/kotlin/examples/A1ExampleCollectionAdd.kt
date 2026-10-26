package gog.my_project.collection.examples

import examples.UserModel
import gog.my_project.collection.Collection

class A1ExampleCollectionAdd {
    fun run() {
        val users = Collection<UserModel>()
        users.add(UserModel(1, "Ada", "ada@example.com"))
        users.add(UserModel(2, "Grace", null))

        println("Added ${users.size} users")
    }
}
