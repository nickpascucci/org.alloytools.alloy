package edu.mit.csail.sdg.alloy4viz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import edu.mit.csail.sdg.alloy4graph.DotShape;

public class DomainShapesTest {

    @Test
    public void namesOfEverydayThingsGetTheirShape() {
        assertEquals(DotShape.DOCUMENT, DomainShapes.forName("File"));
        assertEquals(DotShape.FOLDER, DomainShapes.forName("Dir"));
        assertEquals(DotShape.FOLDER, DomainShapes.forName("Directory"));
        assertEquals(DotShape.ENVELOPE, DomainShapes.forName("Message"));
        assertEquals(DotShape.MAILBOX, DomainShapes.forName("Mailbox"));
        assertEquals(DotShape.LOCK, DomainShapes.forName("Lock"));
        assertEquals(DotShape.KEY, DomainShapes.forName("Key"));
        assertEquals(DotShape.PERSON, DomainShapes.forName("Person"));
        assertEquals(DotShape.DATABASE, DomainShapes.forName("Database"));
    }

    @Test
    public void theModulePathCaseAndPluralDoNotMatter() {
        assertEquals(DotShape.DOCUMENT, DomainShapes.forName("this/File"));
        assertEquals(DotShape.FOLDER, DomainShapes.forName("fs/dirs"));
        assertEquals(DotShape.MAILBOX, DomainShapes.forName("MailBox"));
        assertEquals(DotShape.PERSON, DomainShapes.forName("USER"));
    }

    @Test
    public void theLastWordOfACompoundNameDecides() {
        assertEquals(DotShape.FOLDER, DomainShapes.forName("RootDir"));
        assertEquals(DotShape.KEY, DomainShapes.forName("Room_Key"));
        assertNull(DomainShapes.forName("KeyCard"));
        assertNull(DomainShapes.forName("UserName"));
        assertNull(DomainShapes.forName("Profile"));
        assertNull(DomainShapes.forName("Node"));
        assertNull(DomainShapes.forName(""));
    }

    private static final String MODEL = String.join("\n", "abstract sig Object {}", "sig File, Dir extends Object {}", "sig Person { owns: set Object }", "sig Student extends Person {}", "sig Gadget {}", "run { some File and some Dir and some Student and some Gadget and some Person - Student } for 4");

    @Test
    public void magicLayoutGivesMatchingSigsTheirShapeAndSubsigsInheritIt() throws Exception {
        AlloyInstance instance = TraceFixture.states(MODEL).get(0);
        VizState theme = new VizState(instance);
        MagicLayout.magic(theme);
        MagicColor.magic(theme);
        AlloyModel model = theme.getCurrentModel();
        assertEquals(DotShape.DOCUMENT, theme.shape.resolve(model.hasType("File")));
        assertEquals(DotShape.FOLDER, theme.shape.resolve(model.hasType("Dir")));
        assertEquals(DotShape.PERSON, theme.shape.resolve(model.hasType("Person")));
        assertEquals(DotShape.PERSON, theme.shape.resolve(model.hasType("Student")));
        assertTrue(theme.shape.resolve(model.hasType("Gadget")).ordinal() < DotShape.FOLDER.ordinal());
    }
}
