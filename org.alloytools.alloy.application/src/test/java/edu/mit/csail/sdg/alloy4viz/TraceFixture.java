package edu.mit.csail.sdg.alloy4viz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.Module;
import edu.mit.csail.sdg.parser.CompUtil;
import edu.mit.csail.sdg.translator.A4Options;
import edu.mit.csail.sdg.translator.A4Solution;
import edu.mit.csail.sdg.translator.TranslateAlloyToKodkod;

/** Solves small models and hands their instances to tests. */
final class TraceFixture {

    private TraceFixture() {}

    /**
     * A trash can whose only run has four distinct states: three files; one of
     * them trashed; the trash emptied; another one trashed.
     */
    static final String TRASH  = String.join("\n", "var sig File {}", "var sig Trash in File {}", "one sig Root { var keeps: set File }", "pred delete [f: File] { f not in Trash and Trash' = Trash + f and File' = File }", "pred empty { some Trash and no Trash' and File' = File - Trash }", "pred stutter { File' = File and Trash' = Trash }", "fact {", "  no Trash", "  always keeps = Root -> (File - Trash)", "  always ((some f: File | delete[f]) or empty or stutter)", "}", "run story {", "  #File = 3", "  some f: File | delete[f] and after (empty and after (some g: File | delete[g] and after always stutter))", "} for 3");

    /** A model without variable parts, with two different instances. */
    static final String STATIC = String.join("\n", "sig Node { link: lone Node }", "run chain { #Node = 3 and some link } for 3");

    /** Solves the first command of the model and writes its solution to a new XML file. */
    static File solve(String model) throws Exception {
        File als = File.createTempFile("fixture", ".als");
        als.deleteOnExit();
        Files.write(als.toPath(), model.getBytes(StandardCharsets.UTF_8));
        Module world = CompUtil.parseEverything_fromFile(A4Reporter.NOP, null, als.getAbsolutePath());
        Command command = world.getAllCommands().get(0);
        A4Solution sol = TranslateAlloyToKodkod.execute_command(A4Reporter.NOP, world.getAllReachableSigs(), command, new A4Options());
        if (!sol.satisfiable())
            throw new IllegalStateException("the fixture model has no instance");
        File xml = File.createTempFile("fixture", ".xml");
        xml.deleteOnExit();
        sol.writeXML(xml.getAbsolutePath(), world.getAllFunc());
        return xml;
    }

    /** Solves the first command of the model and returns all states of its instance. */
    static List<AlloyInstance> states(String model) throws Exception {
        return StaticInstanceReader.parseInstances(solve(model));
    }
}
