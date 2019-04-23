package org.cryptomator.common.streams;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.BaseStream;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

@SuppressWarnings({"unchecked", "rawtypes"})
@RunWith(Theories.class)
public class AutoClosingStreamTest {

	private static final Predicate A_PREDICATE = any -> true;
	private static final Function A_FUNCTION = any -> null;
	private static final ToDoubleFunction A_TO_DOUBLE_FUNCTION = any -> 0d;
	private static final ToIntFunction A_TO_INT_FUNCTION = any -> 1;
	private static final ToLongFunction A_TO_LONG_FUNCTION = any -> 1L;
	private static final Consumer A_CONSUMER = any -> {
	};
	private static final Comparator A_COMPARATOR = (left, right) -> 0;
	private static final Collector A_COLLECTOR = mock(Collector.class);
	private static final BinaryOperator A_BINARY_OPERATOR = (left, right) -> null;
	private static final Object AN_OBJECT = new Object();
	private static final IntFunction AN_INT_FUNCTION = i -> null;
	private static final BiConsumer A_BICONSUMER = (a, b) -> {
	};
	private static final Supplier A_SUPPLIER = () -> null;

	@DataPoints("intermediateOperations")
	public static final List<IntermediateOperation<?>> INTERMEDIATE_OPERATIONS = new ArrayList<>();

	@DataPoints("terminalOperations")
	public static final List<TerminalOperation<?>> TERMINAL_OPERATIONS = new ArrayList<>();

	static {
		// define intermediate operations
		test(Stream.class, Stream::distinct);
		test(Stream.class, stream -> stream.filter(A_PREDICATE));
		test(Stream.class, stream -> stream.flatMap(A_FUNCTION));
		test(DoubleStream.class, stream -> stream.flatMapToDouble(A_FUNCTION));
		test(IntStream.class, stream -> stream.flatMapToInt(A_FUNCTION));
		test(LongStream.class, stream -> stream.flatMapToLong(A_FUNCTION));
		test(Stream.class, stream -> stream.limit(5));
		test(Stream.class, stream -> stream.map(A_FUNCTION));
		test(DoubleStream.class, stream -> stream.mapToDouble(A_TO_DOUBLE_FUNCTION));
		test(IntStream.class, stream -> stream.mapToInt(A_TO_INT_FUNCTION));
		test(LongStream.class, stream -> stream.mapToLong(A_TO_LONG_FUNCTION));
		test(Stream.class, Stream::parallel);
		test(Stream.class, stream -> stream.peek(A_CONSUMER));
		test(Stream.class, Stream::sequential);
		test(Stream.class, stream -> stream.skip(5));
		test(Stream.class, Stream::sorted);
		test(Stream.class, stream -> stream.sorted(A_COMPARATOR));
		test(Stream.class, Stream::unordered);

		// define terminal operations
		test(stream -> stream.allMatch(A_PREDICATE), true);
		test(stream -> stream.anyMatch(A_PREDICATE), true);
		test(stream -> stream.collect(A_COLLECTOR), new Object());
		test(stream -> stream.collect(A_SUPPLIER, A_BICONSUMER, A_BICONSUMER), new Object());
		test(Stream::count, 3L);
		test(Stream::findAny, Optional.of(new Object()));
		test(Stream::findFirst, Optional.of(new Object()));
		test(stream -> stream.forEach(A_CONSUMER));
		test(stream -> stream.forEachOrdered(A_CONSUMER));
		test(stream -> stream.max(A_COMPARATOR), Optional.of(new Object()));
		test(stream -> stream.min(A_COMPARATOR), Optional.of(new Object()));
		test(stream -> stream.noneMatch(A_PREDICATE), true);
		test(stream -> stream.reduce(A_BINARY_OPERATOR), Optional.of(new Object()));
		test(stream -> stream.reduce(AN_OBJECT, A_BINARY_OPERATOR), Optional.of(new Object()));
		test(stream -> stream.reduce(AN_OBJECT, A_BINARY_OPERATOR, A_BINARY_OPERATOR), Optional.of(new Object()));
		test(Stream::toArray, new Object[1]);
		test(stream -> stream.toArray(AN_INT_FUNCTION), new Object[1]);
	}

	private static <T> void test(Consumer<Stream> consumer) {
		TERMINAL_OPERATIONS.add(new TerminalOperation<T>() {
			@Override
			public T result() {
				return null;
			}

			@Override
			public T apply(Stream stream) {
				consumer.accept(stream);
				return null;
			}
		});
	}

	private static <T> void test(Function<Stream, T> function, T result) {
		TERMINAL_OPERATIONS.add(new TerminalOperation<T>() {
			@Override
			public T result() {
				return result;
			}

			@Override
			public T apply(Stream stream) {
				return function.apply(stream);
			}
		});
	}

	private static <T extends BaseStream> void test(Class<? extends T> type, Function<Stream, T> function) {
		INTERMEDIATE_OPERATIONS.add(new IntermediateOperation<T>() {
			@Override
			public Class<? extends T> type() {
				return type;
			}

			@Override
			public T apply(Stream stream) {
				return function.apply(stream);
			}
		});
	}

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	private Stream<Object> delegate;
	private Stream<Object> inTest;

	@Before
	public void setUp() {
		delegate = mock(Stream.class);
		inTest = AutoClosingStream.from(delegate);
	}

	@Theory
	public void testIntermediateOperationReturnsNewAutoClosingStream(@FromDataPoints("intermediateOperations") IntermediateOperation intermediateOperation) {
		BaseStream newDelegate = (BaseStream) mock(intermediateOperation.type());
		when(intermediateOperation.apply(delegate)).thenReturn(newDelegate);

		BaseStream result = intermediateOperation.apply(inTest);

		assertThat(result, isAutoClosing());
		verifyDelegate(result, newDelegate);
	}

	@Theory
	public void testTerminalOperationDelegatesToAndClosesDelegate(@FromDataPoints("terminalOperations") TerminalOperation terminalOperation) {
		Object expectedResult = terminalOperation.result();
		if (expectedResult != null) {
			when(terminalOperation.apply(delegate)).thenReturn(expectedResult);
		}

		Object result = terminalOperation.apply(inTest);

		InOrder inOrder = inOrder(delegate);
		assertThat(result, is(expectedResult));
		inOrder.verify(delegate).close();
	}

	@Theory
	public void testTerminalOperationClosesDelegateEvenOnException(@FromDataPoints("terminalOperations") TerminalOperation terminalOperation) {
		RuntimeException exception = new RuntimeException();
		terminalOperation.apply(doThrow(exception).when(delegate));

		thrown.expect(is(exception));

		try {
			terminalOperation.apply(inTest);
		} finally {
			verify(delegate).close();
		}
	}

	private Matcher<BaseStream> isAutoClosing() {
		return is(anyOf(instanceOf(AutoClosingStream.class), instanceOf(AutoClosingDoubleStream.class), instanceOf(AutoClosingIntStream.class), instanceOf(AutoClosingLongStream.class)));
	}

	private void verifyDelegate(BaseStream result, BaseStream newDelegate) {
		result.close();
		verify(newDelegate).close();
	}

	private interface TerminalOperation<T> {

		T result();

		T apply(Stream stream);

	}

	private interface IntermediateOperation<T extends BaseStream> {

		Class<? extends T> type();

		T apply(Stream stream);

	}

}
