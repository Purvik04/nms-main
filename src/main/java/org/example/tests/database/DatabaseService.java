package org.example.tests.database;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.example.Main;

@ProxyGen
public interface DatabaseService {

    static DatabaseService create()
    {
        return new DatabaseServiceImpl();
    }

    static DatabaseService createProxy(String address)
    {
        return new DatabaseServiceVertxEBProxy(Main.getVertx(), address);
    }

    Future<JsonObject> executeQuery(JsonObject query);
}
