package org.jbpm.services.task.jaxb;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.jbpm.services.task.MvelFilePath;
import org.jbpm.services.task.commands.CancelDeadlineCommand;
import org.jbpm.services.task.commands.CompositeCommand;
import org.jbpm.services.task.commands.ProcessSubTaskCommand;
import org.jbpm.services.task.commands.SkipTaskCommand;
import org.jbpm.services.task.commands.StartTaskCommand;
import org.jbpm.services.task.commands.TaskCommand;
import org.jbpm.services.task.commands.UserGroupCallbackTaskCommand;
import org.jbpm.services.task.impl.factories.TaskFactory;
import org.jbpm.services.task.impl.model.UserImpl;
import org.jbpm.services.task.impl.model.xml.JaxbTask;
import org.jbpm.services.task.impl.model.xml.JaxbTaskSummary;
import org.jbpm.services.task.query.TaskSummaryImpl;
import static org.junit.Assert.*;
import org.junit.Assume;
import org.junit.Test;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.User;
import org.kie.internal.task.api.TaskModelProvider;
import org.kie.internal.task.api.model.InternalAttachment;
import org.kie.internal.task.api.model.InternalComment;
import org.kie.internal.task.api.model.InternalOrganizationalEntity;
import org.kie.internal.task.api.model.InternalTask;
import org.kie.internal.task.api.model.InternalTaskData;
import org.kie.internal.task.api.model.SubTasksStrategy;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTaskSerializationTest {

    protected final Logger logger;

    public AbstractTaskSerializationTest() {
        logger = LoggerFactory.getLogger(this.getClass());
    }

    public abstract <T> T testRoundTrip(T input) throws Exception;

    public abstract TestType getType();

    public abstract void addClassesToSerializationContext(Class<?>... extraClass);

    public enum TestType {
        JAXB, JSON, YAML;
    }

    protected Reflections reflections = new Reflections(ClasspathHelper.forPackage("org.jbpm.services.task"),
            new TypeAnnotationsScanner(), new FieldAnnotationsScanner(), new MethodAnnotationsScanner(), new SubTypesScanner());

    // TESTS ----------------------------------------------------------------------------------------------------------------------

    @Test
    public void jaxbTaskTest() throws Exception {
        // Yaml serialization requires major changes in order to be supported.. :/
        Assume.assumeTrue(!getType().equals(TestType.YAML));

        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("now", new Date());

        Reader reader = new InputStreamReader(getClass().getResourceAsStream(MvelFilePath.FullTask));
        Task task = (Task) TaskFactory.evalTask(reader, vars);
        ((InternalTask) task).setFormName("Bruno's Form");
        InternalTaskData taskData = (InternalTaskData) task.getTaskData();

        String payload = "brainwashArmitageRecruitCaseGetPasswordFromLady3JaneAscentToStraylightIcebreakerUniteWithNeuromancer";

        InternalComment comment = (InternalComment) TaskModelProvider.getFactory().newComment();
        comment.setId(42);
        comment.setText(payload);
        comment.setAddedAt(new Date());
        User user = TaskModelProvider.getFactory().newUser();
        ((InternalOrganizationalEntity) user).setId("Case");
        ;
        comment.setAddedBy(user);
        taskData.addComment(comment);

        InternalAttachment attach = (InternalAttachment) TaskModelProvider.getFactory().newAttachment();
        attach.setId(1);
        attach.setName("virus");
        attach.setContentType("ROM");
        attach.setAttachedAt(new Date());
        user = TaskModelProvider.getFactory().newUser();
        ((InternalOrganizationalEntity) user).setId("Wintermute");
        ;
        attach.setAttachedBy(user);
        attach.setSize(payload.getBytes().length);
        attach.setAttachmentContentId(comment.getId());
        taskData.addAttachment(attach);

        JaxbTask xmlTask = new JaxbTask(task);
        assertNotNull(xmlTask.getNames());
        assertTrue(xmlTask.getNames().size() > 0);
        JaxbTask bornAgainTask = testRoundTrip(xmlTask);
        assertNotNull(bornAgainTask.getNames());
        assertTrue("Round-tripped task has empty 'names' list!", !bornAgainTask.getNames().isEmpty());

        ComparePair compare = new ComparePair(task, bornAgainTask, Task.class);
        compare.recursiveCompare();

        assertNotNull(((InternalTask) xmlTask).getFormName());
        assertEquals(((InternalTask) xmlTask).getFormName(), ((InternalTask) bornAgainTask).getFormName());
    }

    @Test
    public void taskCommandSubTypesCanBeSerialized() throws Exception {
        // Yaml serialization not required for commands
        Assume.assumeTrue(!getType().equals(TestType.YAML));

        for (Class<?> jaxbClass : reflections.getSubTypesOf(TaskCommand.class)) {
            if (jaxbClass.equals(UserGroupCallbackTaskCommand.class)) {
                continue;
            }
            addClassesToSerializationContext(jaxbClass);
            Constructor<?> construct = jaxbClass.getConstructor(new Class[] {});
            Object jaxbInst = construct.newInstance(new Object[] {});
            try {
                testRoundTrip(jaxbInst);
            } catch (Exception e) {
                logger.warn("Testing failed for" + jaxbClass.getName());
                throw e;
            }
        }
    }

    @Test
    public void taskCompositeCommandCanBeSerialized() throws Exception {
        addClassesToSerializationContext(CompositeCommand.class);
        addClassesToSerializationContext(StartTaskCommand.class);
        addClassesToSerializationContext(CancelDeadlineCommand.class);
        CompositeCommand<Void> cmd = new CompositeCommand<Void>(new StartTaskCommand(1, "john"), new CancelDeadlineCommand(1, true,
                false));

        CompositeCommand<?> returned = testRoundTrip(cmd);
        assertNotNull(returned);
        assertNotNull(returned.getMainCommand());
        assertTrue(returned.getMainCommand() instanceof StartTaskCommand);
        assertEquals(Long.valueOf(1), returned.getTaskId());
        assertNotNull(returned.getCommands());
        assertEquals(1, returned.getCommands().length);

    }

    @Test
    public void taskCompositeCommandMultipleCanBeSerialized() throws Exception {
        addClassesToSerializationContext(CompositeCommand.class);
        addClassesToSerializationContext(SkipTaskCommand.class);
        addClassesToSerializationContext(ProcessSubTaskCommand.class);
        addClassesToSerializationContext(CancelDeadlineCommand.class);
        CompositeCommand<Void> cmd = new CompositeCommand<Void>(new SkipTaskCommand(1, "john"),
                new ProcessSubTaskCommand(1, "john"), new CancelDeadlineCommand(1, true, true));

        CompositeCommand<?> returned = testRoundTrip(cmd);
        assertNotNull(returned);
        assertNotNull(returned.getMainCommand());
        assertTrue(returned.getMainCommand() instanceof SkipTaskCommand);
        assertEquals(Long.valueOf(1), returned.getTaskId());
        assertNotNull(returned.getCommands());
        assertEquals(2, returned.getCommands().length);
    }

    @Test
    public void jaxbTaskSummarySerialization() throws Exception {
        Assume.assumeFalse(getType().equals(TestType.YAML));

        TaskSummaryImpl taskSumImpl = new TaskSummaryImpl(
                1l, 
                "a", "b", "c", 
                Status.Completed, 
                3, true, 
                new UserImpl("d"), new UserImpl("e"), 
                new Date(), new Date(), new Date(), 
                "f", 5, 2l, "deploymentId",
                SubTasksStrategy.EndParentOnAllSubTasksEnd, 6l);

        JaxbTaskSummary jaxbTaskSum = new JaxbTaskSummary(taskSumImpl);
        JaxbTaskSummary jaxbTaskSumCopy = testRoundTrip(jaxbTaskSum);

        ComparePair.compareObjectsViaFields(jaxbTaskSum, jaxbTaskSumCopy, "subTaskStrategy", "potentialOwners");
    }
}
