package edu.uic.cs554.project;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Main Behavior for an actor
 */
public class ActorMain extends AbstractBehavior<ActorMain.Command> {

    private static final Logger logger = LoggerFactory.getLogger(ActorMain.class);

    public interface Command extends CborSerializable {
    }

    public final static class StartHashing implements Command {
        private final String value;

        public StartHashing(String value) {
            this.value = value;
        }
    }

    public ActorMain(ActorContext<Command> context) {
        super(context);
    }

    private static EntityRef<Command> mainActor;
    private static List<EntityRef<Hasher.Command>> hashers = new ArrayList<>();

    public static Behavior<Command> create(List<edu.uic.cs554.project.Actor> aList) {
        return Behaviors.setup(context -> {
            ClusterSharding clusterSharding = ClusterSharding.get(context.getSystem());
            EntityTypeKey<Hasher.Command> typeKey = EntityTypeKey.create(Hasher.Command.class, "Hasher");

            clusterSharding.init(Entity.of(typeKey, ctx -> Hasher.create(ctx.getEntityId())));

            // Iterate through list of actors
            for (Actor person : aList) {
                System.out.println("Creating actor: " + person.getActorName());
                String hasherIndex = person.getActorName();
                hashers.add(clusterSharding.entityRefFor(typeKey, hasherIndex));
            }

            EntityTypeKey<ActorMain.Command> typeKeyForMainActor = EntityTypeKey.create(ActorMain.Command.class, "MainActor");
            clusterSharding.init(Entity.of(typeKeyForMainActor, ctx -> ActorMain.createActor()));

            mainActor = clusterSharding.entityRefFor(typeKeyForMainActor, "mainActor");
            System.out.println("Starting ");
            for (EntityRef<Hasher.Command> hasher : hashers) {
                hasher.tell(new Hasher.GetHash(mainActor));
            }

            return Behaviors.empty();
        });
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(StartHashing.class, this::onStartHashing)
                .onMessage(HashedMessagedReceived.class, this::onHashMessageReceive).build();
    }

    public static Behavior<Command> createActor() {
        return Behaviors.setup(ActorMain::new);
    }

    public final static class HashedMessagedReceived implements Command {
        public final String entityId;
        public final String message;

        public HashedMessagedReceived(String entityId, String message) {
            this.entityId = entityId;
            this.message = message;
        }

        @Override
        public int hashCode() {
            return Objects.hash(entityId, message);
        }
    }

    private Behavior<Command> onHashMessageReceive(HashedMessagedReceived messageReceived) {
        int hasherNumber = Integer.parseInt(messageReceived.entityId);

        String oldHash = messageReceived.message;

        System.out.println("Old hash from " + hasherNumber + ", " + oldHash);
        System.out.println("New hash: " + messageReceived.hashCode());
        logger.info("Old hash from " + hasherNumber + ", " + oldHash);
        logger.info("New hash: " + messageReceived.hashCode());
        // TODO: Do something with hash

        hashers.get(hasherNumber).tell(new Hasher.GetHash(mainActor));
        return this;
    }


    private Behavior<Command> onStartHashing(StartHashing command) {
        getContext().getLog().info("Started Making hash ");
        System.out.println("Started making hash");
        return this;
    }
}
