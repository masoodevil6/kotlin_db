package gog.my_project.data_base.core.data_base


class DatabaseConfigBuilder {
    private var domain:     String   = ""
    private var port:       Int?     = null
    private var name:       String   = ""
    private var username:   String   = ""
    private var password:   String   = ""
    private var dbPoolSize: Int      = 10

    fun domain(value: String) =   apply { domain = value }
    fun port(value: Int) =        apply { port = value }
    fun name(value: String) =     apply { name = value }
    fun username(value: String) = apply { username = value }
    fun password(value: String) = apply { password = value }
    fun poolSize(value: Int) =    apply { dbPoolSize = value }

    fun build() = DatabaseConfig(
        dbDomain = domain,
        dbPort = port,
        dbName = name,
        dbUserName = username,
        dbPassword = password,
        dbPoolSize = dbPoolSize
    )
}