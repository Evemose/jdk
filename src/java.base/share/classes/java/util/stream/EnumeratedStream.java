package java.util.stream;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * A sequence of elements supporting parallel and sequential aggregate operations.
 * The difference between this interface and {@link Stream} is that this interface
 * provides additional methods for working with the elements in an enumerated fashion.
 * @param <T> the type of the stream elements
 */
public interface EnumeratedStream<T> extends Stream<T> {
    /**
     * Returns a stream consisting of the elements of this stream that match
     * the given predicate.
     * @param predicate a non-interfering, stateless predicate to apply to each element to determine if it
     * @return the new stream
     */
    EnumeratedStream<T> filter(BiPredicate<Long, ? super T> predicate);
    /**
     * Returns a stream consisting of the results of applying the given
     * function to the elements of this stream.
     * @param mapper a non-interfering, stateless function to apply to each element
     * @param <R> The element type of the new stream
     * @return the new stream
     */
    <R> EnumeratedStream<R> map(BiFunction<Long, ? super T, ? extends R> mapper);
    /**
     * Returns a stream consisting of the results of replacing each element of
     * this stream with the contents of a mapped stream produced by applying
     * the provided mapping function to each element.
     * @param mapper a non-interfering, stateless function to apply to each element which produces a stream of new values
     * @param <R> The element type of the new stream
     * @return the new stream
     */
    <R> EnumeratedStream<R> flatMap(BiFunction<Long, ? super T, ? extends Stream<? extends R>> mapper);
    /**
     * Returns a stream consisting of the elements of this stream, additionally
     * performing the provided action on each element as elements are consumed
     * from the resulting stream.
     * @param action a non-interfering action to perform on the elements as they are consumed from the stream
     * @return the new stream
     */
    EnumeratedStream<T> peek(BiConsumer<Long, ? super T> action);
    /**
     * Returns a stream consisting of the elements of this stream, truncated
     * when the given predicate returns false for the first time.
     * @param predicate a non-interfering, stateless predicate to apply to elements to determine the cut-off
     * @return the new stream
     */
    EnumeratedStream<T> takeWhile(BiPredicate<Long, ? super T> predicate);
    /**
     * Returns a stream consisting of the remaining elements of this stream
     * after dropping the longest prefix of elements that match the given
     * predicate.
     * @param predicate a non-interfering, stateless predicate to apply to elements to determine the drop condition
     * @return the new stream
     */
    EnumeratedStream<T> dropWhile(BiPredicate<Long, ? super T> predicate);

    /**
     * Returns a stream consisting of the elements of this stream, enumerated.
     * @param <T> the type of the stream elements
     * @param values the elements of the new stream
     * @return the new stream
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    static <T> EnumeratedStream<T> of(T... values) {
        return Arrays.stream(values).enumerate();
    }
}
