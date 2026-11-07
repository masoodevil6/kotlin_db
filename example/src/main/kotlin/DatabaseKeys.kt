package gog.my_project.example

import gog.my_project.data_base_manager.facade.DatabaseKey

object MainDatabase : DatabaseKey
data class AnalyticsDatabase(val region: String) : DatabaseKey
