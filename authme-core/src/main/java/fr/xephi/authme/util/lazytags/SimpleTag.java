package fr.xephi.authme.util.lazytags;

import java.util.function.Supplier;

/**
 * Tag to be replaced that does not depend on an argument.
 *
 * @param <A> type of the argument (not used in this implementation)
 */
public class SimpleTag<A> extends DependentTag<A> {

    public SimpleTag(String name, Supplier<String> replacementFunction) {
        super(name, argument -> replacementFunction.get());
    }
}
