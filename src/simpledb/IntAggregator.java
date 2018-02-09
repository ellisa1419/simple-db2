package simpledb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * An {@code IntAggregator} computes some aggregate value over a set of {@code IntField}s.
 */
public class IntAggregator implements Aggregator {

	/**
	 * A {@code IntAggregatorImpl} instance.
	 */
	IntAggregatorImpl impl;

	/**
	 * Constructs an {@code IntAggregator}.
	 * 
	 * @param gbfield
	 *            the 0-based index of the group-by field in the tuple, or {@code NO_GROUPING} if there is no grouping
	 * @param gbfieldtype
	 *            the type of the group by field (e.g., {@code Type.INT_TYPE}), or {@code null} if there is no grouping
	 * @param afield
	 *            the 0-based index of the aggregate field in the tuple
	 * @param what
	 *            the aggregation operator
	 */
	public IntAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
		if (gbfield == NO_GROUPING)
			impl = new IntAggregatorImplWithOutGrouping(afield, what);
		else
			impl = new IntAggregatorImplWithGrouping(gbfield, gbfieldtype, afield, what);

	}

	/**
	 * Merges a new {@code Tuple} into the aggregate, grouping as indicated in the constructor.
	 * 
	 * @param tup
	 *            the {@code Tuple} containing an aggregate field and a group-by field
	 */
	public void merge(Tuple tup) {
		impl.merge(tup);
	}

	/**
	 * Creates a {@code DbIterator} over group aggregate results.
	 *
	 * @return a {@code DbIterator} whose tuples are the pair ({@code groupVal}, {@code aggregateVal}) if using group,
	 *         or a single ({@code aggregateVal}) if no grouping. The {@code aggregateVal} is determined by the type of
	 *         aggregate specified in the constructor.
	 */
	public DbIterator iterator() {
		return impl.iterator();
	}

	/**
	 * An {@code IntAggregatorImpl} computes some aggregate value over a set of {@code Field}s.
	 */
	abstract class IntAggregatorImpl {
				
		// "ALOT" of code goes here ;)))
		/**
		 * The 0-based index of the aggregate field
		 */
		int afield;

		/**
		 * An {@code AggregateFunctionFactory} for creating an {@code AggregateFunction} whenever needed.
		 */
		AggregateFunctionFactory aggFtnFactory;
		IntAggregatorImpl(int afield, Op what) {
			switch (what) {
			case COUNT:
				this.aggFtnFactory = new AggregateFunctionFactory() {

					@Override
					public AggregateFunction createAggregateFunction() {
						return new CountAggregateFunction();
					}
				};
			case MIN:
				this.aggFtnFactory= new AggregateFunctionFactory() {
					
					@Override
					public AggregateFunction createAggregateFunction() {
						return new MinAggregateFunction();
					}
				};
				break;
			case MAX:
				this.aggFtnFactory= new AggregateFunctionFactory() {
					
					@Override
					public AggregateFunction createAggregateFunction() {
						return new MaxAggregateFunction();
					}
				};
				break;
			case SUM:
				this.aggFtnFactory= new AggregateFunctionFactory() {
					
					@Override
					public AggregateFunction createAggregateFunction() {
						return new IntSumAggregateFunction();
					}
				};
				break;
			case AVG:
				this.aggFtnFactory= new AggregateFunctionFactory() {
					
					@Override
					public AggregateFunction createAggregateFunction() {
						return new IntAverageAggregateFunction();
					}
				};
				break;
			
			
			
			default:
				throw new IllegalArgumentException(what + " is not supported yet!! :))");
			}
			this.afield = afield;
		}
		

		// no need of java doc ;)
		public abstract void merge(Tuple tup);

		public abstract DbIterator iterator();

		public abstract void clear();	
	}
	class IntAggregatorImplWithOutGrouping extends IntAggregatorImpl {

		TupleDesc td;
		
		AggregateFunction aggFtn;
		
		IntAggregatorImplWithOutGrouping(int afield, Op what) {
			super(afield, what);
			Type[] type = new Type[] { Type.INT_TYPE };
			td = new TupleDesc(type);
			aggFtn = this.aggFtnFactory.createAggregateFunction();
		}
		
		ArrayList<Tuple> tuples = new ArrayList<Tuple>();
		@Override
		public void merge(Tuple tup) {
			aggFtn.merge(tup.getField(afield));
			tuples.add(tup);
		}

		@Override
		public DbIterator iterator() {
			return new TupleIterator(this.td, tuples);
		}

		@Override
		public void clear() {
			aggFtn = aggFtnFactory.createAggregateFunction();
			
		}
		
	}
	class IntAggregatorImplWithGrouping extends IntAggregatorImpl {

		TupleDesc td;

		/**
		 * The 0-based index of the group-by field in the tuple.
		 */
		int gbfield;
		
		/**
		 * A map that associates each group value with an {@code AggregateFunction}.
		 */
		Map<Field, AggregateFunction> field2aggFtn = new HashMap<Field, AggregateFunction>();

		
		IntAggregatorImplWithGrouping(int gbfield, Type gbfieldtype, int afield, Op what) {
			super(afield, what);
		}

		@Override
		public void merge(Tuple tup) {
			AggregateFunction aggFtn = this.aggFtnFactory.createAggregateFunction();
			
			// explanation : For the return tuple : we need first field of type  "group-by" hence read from the input tuple
			// and second will always be aggregate, so put 'INT_TYPE'
			Field field= tup.getField(gbfield);
			Type[] type= {tup.getTupleDesc().types[gbfield], Type.INT_TYPE};  
			String[] names=tup.getTupleDesc().names;
			td= new TupleDesc(type, names);
			
			if(field2aggFtn.containsKey(field)){
				 aggFtn=field2aggFtn.get(field);   // we already have count for this, lets just get it 
			}
			aggFtn.merge(tup.getField(afield));
			field2aggFtn.put(field, aggFtn);
		}

		@Override
		public DbIterator iterator() {
			ArrayList<Tuple> tuples= new ArrayList<Tuple>();
			 for (Entry<Field, AggregateFunction> entry : field2aggFtn.entrySet()) {
				    Tuple tuple= new Tuple(td);
				    tuple.setField(0, entry.getKey());
				    Field f=entry.getValue().aggregateValue();
				    tuple.setField(1, f);
				    tuples.add(tuple);
				}
			 return new TupleIterator(td, tuples);
		
		}

		@Override
		public void clear() {
			field2aggFtn.clear();
			
		}
		
	}

	/**
	 * Clears this {@code IntAggregator}.
	 */
	@Override
	public void clear() {
		impl.clear();
	}
}
