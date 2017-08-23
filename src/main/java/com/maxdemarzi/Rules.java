package com.maxdemarzi;

import com.maxdemarzi.quine.BooleanExpression;
import com.maxdemarzi.results.StringResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;

import java.io.IOException;
import java.util.stream.Stream;

public class Rules {
    // This field declares that we need a GraphDatabaseService
    // as context when any procedure in this class is invoked
    @Context
    public GraphDatabaseService db;
    // This gives us a log instance that outputs messages to the
    // standard log, normally found under `data/log/console.log`
    @Context
    public Log log;

    // This procedure creates a rule, ex. ("Rule 1", "(0 & 1) | (2 & 3)")
    @Procedure(name = "com.maxdemarzi.rules.create", mode = Mode.WRITE)
    @Description("CALL com.maxdemarzi.rules.create(id, formula) - create a rule")
    public Stream<StringResult> create(@Name("id") String id, @Name("formula") String formula) throws IOException {
        // See if the rule already exists
        Node rule = db.findNode(Labels.Rule, "id", id);
        if (rule == null) {
            // Create the rule
            rule = db.createNode(Labels.Rule);
            rule.setProperty("id", id);
            rule.setProperty("formula", formula);

            // Use the expression to find the required paths
            BooleanExpression boEx = new BooleanExpression(formula);
            boEx.doTabulationMethod();
            boEx.doQuineMcCluskey();
            boEx.doPetricksMethod();

            // Create a relationship from the lead attribute node to the path nodes
            for (String path : boEx.getPathExpressions()) {
                Node pathNode = db.findNode(Labels.Path, "id", path);
                if (pathNode == null) {
                    // Create the path node if it doesn't already exist
                    pathNode = db.createNode(Labels.Path);
                    pathNode.setProperty("id", path);

                    // Create the attribute nodes if they don't already exist
                    String[] attributes = path.split("[!&]");
                    for (int i = 0; i < attributes.length; i++) {
                        String attributeId = attributes[i];
                        Node attribute = db.findNode(Labels.Attribute, "id", attributeId);
                        if (attribute == null) {
                            attribute = db.createNode(Labels.Attribute);
                            attribute.setProperty("id", attributeId);
                        }
                        // Create the relationship between the lead attribute node to the path node
                        if (i == 0) {
                            Relationship inPath = attribute.createRelationshipTo(pathNode, RelationshipTypes.IN_PATH);
                            inPath.setProperty("path", path);
                        }
                    }

                }

                // Create a relationship between the path and the rule
                pathNode.createRelationshipTo(rule, RelationshipTypes.HAS_RULE);
            }
        }

        return Stream.of(new StringResult("Rule " + formula + " created."));
    }
}
