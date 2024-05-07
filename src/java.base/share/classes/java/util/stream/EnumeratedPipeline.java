package java.util.stream;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;

/**
 * An abstract pipeline class that extends ReferencePipeline and implements EnumeratedStream.
 * This class provides implementations for the methods in EnumeratedStream.
 *
 * @param <P_IN>  the type of the input elements
 * @param <P_OUT> the type of the output elements
 */
abstract class EnumeratedPipeline<P_IN, P_OUT> extends ReferencePipeline<P_IN, P_OUT> implements EnumeratedStream<P_OUT> {

    private long lastEmittedIndex = 0;

    /**
     * Index supplier for a current element.
     */
    private final LongSupplier indexSupplier;


    EnumeratedPipeline(AbstractPipeline<?, P_IN, ?> upstream, int opFlags) {
        this(upstream, opFlags, null);
    }

    EnumeratedPipeline(EnumeratedPipeline<?, P_IN> upstream, int opFlags) {
        this(upstream, opFlags, () -> upstream.lastEmittedIndex);
    }

    /**
     * Constructor for the EnumeratedPipeline.
     * @param upstream the upstream pipeline
     * @param opFlags the operation flags
     * @param indexSupplier the index supplier. null if indexes are not preserved from the upstream
     */
    EnumeratedPipeline(AbstractPipeline<?, P_IN, ?> upstream,  int opFlags, LongSupplier indexSupplier) {
        super(upstream, opFlags);
        this.indexSupplier = Objects.requireNonNullElseGet(indexSupplier, () -> () -> lastEmittedIndex++);
    }

    abstract static class StatelessOp<P_IN, P_OUT>
            extends EnumeratedPipeline<P_IN, P_OUT> {

        StatelessOp(EnumeratedPipeline<?, P_IN> upstream,
                    StreamShape inputShape,
                    int opFlags) {
            super(upstream, opFlags);
            assert upstream.getOutputShape() == inputShape;
        }

        StatelessOp(EnumeratedPipeline<?, P_IN> upstream,
                    StreamShape inputShape,
                    int opFlags,
                    LongSupplier indexSupplier) {
            super(upstream, opFlags, indexSupplier);
            assert upstream.getOutputShape() == inputShape;
        }

        @Override
        final boolean opIsStateful() {
            return false;
        }
    }

    abstract static class StatefulOp<P_IN, P_OUT>
            extends EnumeratedPipeline<P_IN, P_OUT> {

        StatefulOp(EnumeratedPipeline<?, P_IN> upstream,
                    StreamShape inputShape,
                    int opFlags) {
            super(upstream, opFlags);
            assert upstream.getOutputShape() == inputShape;
        }

        StatefulOp(EnumeratedPipeline<?, P_IN> upstream,
                    StreamShape inputShape,
                    int opFlags,
                    LongSupplier indexSupplier) {
            super(upstream, opFlags, indexSupplier);
            assert upstream.getOutputShape() == inputShape;
        }

        @Override
        final boolean opIsStateful() {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EnumeratedStream<P_OUT> filter(BiPredicate<Long, ? super P_OUT> predicate) {
        Objects.requireNonNull(predicate);
        return new StatelessOp<>(this, StreamShape.REFERENCE, StreamOpFlag.NOT_SIZED) {
            @Override
            Sink<P_OUT> opWrapSink(int flags, Sink<P_OUT> sink1) {
                return new Sink.ChainedReference<>(sink1) {
                    @Override
                    public void begin(long size) {
                        downstream.begin(-1);
                    }

                    @Override
                    public void accept(P_OUT u) {
                        if (predicate.test(indexSupplier.getAsLong(), u)) {
                            downstream.accept(u);
                        }
                    }
                };
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> EnumeratedStream<R> map(BiFunction<Long, ? super P_OUT, ? extends R> mapper) {
        Objects.requireNonNull(mapper);
        return new StatelessOp<>(this, StreamShape.REFERENCE, StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT) {
            @Override
            Sink<P_OUT> opWrapSink(int flags, Sink<R> sink1) {
                return new Sink.ChainedReference<>(sink1) {
                    @Override
                    public void accept(P_OUT u) {
                        downstream.accept(mapper.apply(indexSupplier.getAsLong(), u));
                    }
                };
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> EnumeratedStream<R> flatMap(BiFunction<Long, ? super P_OUT, ? extends Stream<? extends R>> mapper) {
        Objects.requireNonNull(mapper);
        return new StatelessOp<>(this, StreamShape.REFERENCE, StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT | StreamOpFlag.NOT_SIZED, null) {
            @Override
            Sink<P_OUT> opWrapSink(int flags, Sink<R> sink1) {
                return new Sink.ChainedReference<>(sink1) {
                    boolean stop = false;

                    @Override
                    public void begin(long size) {
                        downstream.begin(-1);
                    }

                    @Override
                    public void accept(P_OUT u) {
                        try (Stream<? extends R> result = mapper.apply(indexSupplier.getAsLong(), u)) {
                            if (result != null) {
                                if (!stop) {
                                    result.sequential().forEach(downstream);
                                } else {
                                    var resultSpliterator = result.sequential().spliterator();
                                    while (!downstream.cancellationRequested()) {
                                        if (!resultSpliterator.tryAdvance(downstream)) {
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public boolean cancellationRequested() {
                        stop = true;
                        return downstream.cancellationRequested();
                    }
                };
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EnumeratedStream<P_OUT> peek(BiConsumer<Long, ? super P_OUT> action) {
        Objects.requireNonNull(action);
        return new StatelessOp<>(this, StreamShape.REFERENCE, 0) {
            @Override
            Sink<P_OUT> opWrapSink(int flags, Sink<P_OUT> sink1) {
                return new Sink.ChainedReference<>(sink1) {
                    @Override
                    public void accept(P_OUT u) {
                        action.accept(indexSupplier.getAsLong(), u);
                        downstream.accept(u);
                    }
                };
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EnumeratedStream<P_OUT> takeWhile(BiPredicate<Long, ? super P_OUT> predicate) {
        return WhileOps.makeTakeWhileEnumeratedRef(this, predicate, indexSupplier);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EnumeratedStream<P_OUT> dropWhile(BiPredicate<Long, ? super P_OUT> predicate) {
        // dropWhile consumes indexes until a first element that doesn't match the predicate,
        // So we need to update the indexSupplier to return the next index for each remaining element
        return WhileOps.makeDropWhileEnumeratedRef(this, predicate, indexSupplier, () -> lastEmittedIndex++);
    }
}
