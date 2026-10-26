package gog.my_project.collection.examples

import examples.UserModel
import gog.my_project.collection.Collection
import gog.my_project.collection.ICollection

class A5ExampleCollectionMap {
    fun run() {
        val users = Collection<UserModel>()
        users.add(UserModel(1, "Ada", "ada@example.com"))
        users.add(UserModel(2, "Grace", null))

        val names: ICollection<String> = users.map { user -> user.name }

        names.forEach { name -> println(name) }
    }
}
