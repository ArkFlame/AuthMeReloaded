package fr.xephi.authme.util.lazytags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Replaces tags lazily by first determining which tags are being used
 * and only applying those replacements afterwards.
 *
 * @param <A> the argument type
 */
public final class TagReplacer<A> {

    private final List<Tag<A>> tags;
    private final Collection<String> messages;

    /**
     * Private constructor. Use {@link #newReplacer(Collection, Collection)}.
     *
     * @param tags the tags that are being used in the messages
     * @param messages the messages
     */
    private TagReplacer(List<Tag<A>> tags, Collection<String> messages) {
        this.tags = tags;
        this.messages = messages;
    }

    /**
     * Creates a new instance of this class, which will provide the given
     * messages adapted with the provided tags.
     *
     * @param allTags all available tags
     * @param messages the messages to use
     * @param <A> the argument type
     * @return new tag replacer instance
     */
    public static <A> TagReplacer<A> newReplacer(Collection<Tag<A>> allTags, Collection<String> messages) {
        List<Tag<A>> usedTags = determineUsedTags(allTags, messages);
        return new TagReplacer<>(usedTags, messages);
    }

    /**
     * Returns the messages with the tags applied for the given argument.
     *
     * @param argument the argument to get the messages for
     * @return the adapted messages
     */
    public List<String> getAdaptedMessages(A argument) {
        List<String> tagNames = new ArrayList<>(tags.size());
        List<String> tagValues = new ArrayList<>(tags.size());
        for (Tag<A> tag : tags) {
            tagNames.add(tag.getName());
            tagValues.add(tag.getValue(argument));
        }

        List<String> adaptedMessages = new ArrayList<>(messages.size());
        for (String line : messages) {
            String adaptedLine = line;
            for (int i = 0; i < tagNames.size(); i++) {
                adaptedLine = adaptedLine.replace(tagNames.get(i), tagValues.get(i));
            }
            adaptedMessages.add(adaptedLine);
        }
        return adaptedMessages;
    }

    /**
     * Determines which tags are used somewhere in the given list of messages.
     *
     * @param allTags all available tags
     * @param messages the messages
     * @param <A> argument type
     * @return tags used at least once
     */
    private static <A> List<Tag<A>> determineUsedTags(Collection<Tag<A>> allTags, Collection<String> messages) {
        return allTags.stream()
            .filter(tag -> messages.stream().anyMatch(msg -> msg.contains(tag.getName())))
            .collect(Collectors.toList());
    }
}
