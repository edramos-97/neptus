package pt.lsts.neptus.plugins.dataSync;

import java.util.HashSet;
import java.util.Set;

public final class Operations {
    private Operations() {
        // No instances
    }

    public static <E> Set<E> diff(Set<E> firstSet, final Set<E> secondSet) {
        return filtered(firstSet, new Predicate<E>() {
            @Override
            public boolean call(E element) {
                return !secondSet.contains(element);
            }
        });
    }

    public static <E> Set<E> union(Set<E> firstSet, final Set<E> secondSet) {
        final Set<E> newSet = new HashSet<>();
        newSet.addAll(firstSet);
        newSet.addAll(secondSet);
        return newSet;
    }

    public static <E> Set<E> filtered(Set<E> set, Predicate<E> predicate) {
        final Set<E> newSet = new HashSet<>();
        for (E element : set) {
            if (predicate.call(element)) {
                newSet.add(element);
            }
        }
        return newSet;
    }

    public static <E, R> Set<R> filteredAndMapped(Set<E> set, Predicate<E> predicate,
                                                  Mapper<E, R> mapper) {
        final Set<R> newSet = new HashSet<>();
        for (E element : set) {
            if (predicate.call(element)) {
                newSet.add(mapper.call(element));
            }
        }
        return newSet;
    }

    public static <E, R> Set<R> mappedd(Set<E> set, Mapper<E, R> mapper) {
        final Set<R> newSet = new HashSet<>();
        for (E element : set) {
            newSet.add(mapper.call(element));
        }
        return newSet;
    }

    public static <E> E select(Set<E> set, Predicate2<E, E> predicate) {
        if (set.isEmpty()) {
            throw new IllegalArgumentException("Empty set for select operation");
        }
        E winner = set.iterator().next();
        for (E element : set) {
            if (predicate.call(winner, element)) {
                winner = element;
            }
        }
        return winner;
    }


    public interface Predicate<E> {
        boolean call(E element);
    }

    public interface Predicate2<E, F> {
        boolean call(E first, F second);
    }

    public interface Mapper<E, R> {
        R call(E element);
    }
}