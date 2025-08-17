//package com.planbana.backend.migrations;

//import com.mongodb.client.MongoDatabase;
//import io.mongock.api.annotations.ChangeLog;
//import io.mongock.api.annotations.ChangeSet;
//
//@ChangeLog(order = "001")
//public class InitialChangeLog {
//
//  @ChangeSet(order = "001", id = "initIndexes", author = "system")
//  public void initIndexes(MongoDatabase db) {
//    // Index creation can be placed here if needed.
//    // For brevity, relying on Spring Data @Indexed annotations.
//  }
//}
package com.planbana.backend.migrations;

        import com.mongodb.client.MongoDatabase;
        import io.mongock.api.annotations.ChangeUnit;
        import io.mongock.api.annotations.Execution;
        import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(id = "init-indexes", order = "001", author = "system")
public class InitialChangeLog {

  @Execution
  public void execution(MongoDatabase db) {
    // create indexes if needed
  }

  @RollbackExecution
  public void rollback() {
    // optional rollback
  }
}
