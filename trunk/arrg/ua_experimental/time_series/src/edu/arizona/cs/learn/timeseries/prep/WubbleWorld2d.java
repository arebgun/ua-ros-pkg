package edu.arizona.cs.learn.timeseries.prep;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.arizona.cs.learn.timeseries.model.Interval;

/**
 * The purpose of this class is to take in the raw ww2d data and
 * convert it into some set of propositions.  This is not a blind
 * procedure as it has been before.  In this case, we are interested in
 * certain relations and we'll try to find them.
 * @author wkerr
 *
 */
public class WubbleWorld2d {
	
	
	public static double ZERO = 0.01;
	
	private List<String> _headers;
	
	private Map<String,List<Double>> _doubleMap;
	private Map<String,List<String>> _stringMap;
	private Map<String,List<Boolean>> _booleanMap;
	
	public WubbleWorld2d() { 
		
	}
	
	public void load(String input) {
		_headers = new ArrayList<String>();
		Map<String,List<String>> map = new HashMap<String,List<String>>();

		try { 
			BufferedReader in = new BufferedReader(new FileReader(input));
			
			String line = in.readLine();
			String[] tokens = line.split("[,]");
				
			for (String token : tokens) {
				String header = token.replaceAll("[\"]", "");
				_headers.add(header);
				map.put(header, new ArrayList<String>());
			}
			
			while (in.ready()) { 
				line = in.readLine();
				tokens = line.split("[,]");
				
				for (int i = 0; i < tokens.length; ++i) 
					map.get(_headers.get(i)).add(tokens[i]);
			}
			in.close();
		} catch (Exception e) { 
			e.printStackTrace();
		}

		_doubleMap = new HashMap<String,List<Double>>();
		_stringMap = new HashMap<String,List<String>>();
		_booleanMap = new HashMap<String,List<Boolean>>();
		
		// Now iterate through all of the possible columsn and 
		// partition them into the correct sets.
		for (String key : _headers) { 
			
			// determine the type....
			String s = map.get(key).get(0);
			if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) { 
				List<Boolean> list = new ArrayList<Boolean>();
				for (String value : map.get(key)) { 
					list.add(Boolean.parseBoolean(value));
				}
				_booleanMap.put(key, list);
			} else { 
				try { 
					double d = Double.parseDouble(s);
					List<Double> list = new ArrayList<Double>();
					for (String value : map.get(key)) { 
						list.add(Double.parseDouble(value));
					}
					_doubleMap.put(key, list);
				} catch (Exception e) { 
					_stringMap.put(key, map.get(key));
				}
			}
		}
	}
	
	/**
	 * For each distance double, create three possible propositions
	 *  -- DistanceStable
	 *  -- DistanceDecreasing
	 *  -- DistanceIncreasing
	 *  
	 *  Note: For now there is no smoothing going on.
	 *  Note: For now ignore the walls.
	 */
	public void doDistances() { 
		for (String key : _headers) {
			if (!key.startsWith("distance")) 
				continue;
			
			if (key.matches(".*wall.*"))
				continue;
			
			String suffix = key.substring(8);
			System.out.println(suffix);
			
			// assumption is that it should be populated in the 
			// double map.
			List<Double> values = _doubleMap.get(key);
			List<Double> diff = TimeSeries.diff(values);
			
			List<Boolean> dd = new ArrayList<Boolean>();
			List<Boolean> di = new ArrayList<Boolean>();
			List<Boolean> ds = new ArrayList<Boolean>();
			
			// The first difference means that the first value is
			// not a number so none of these propositions can be true.
			dd.add(false);
			di.add(false);
			ds.add(false);
			
			for (Double d : diff) { 
				boolean inc = false;
				boolean dec = false;
				boolean stable = false;
				if (Double.compare(Double.NaN, d) == 0) {
					// do nothing since they are all false
				} else if (d > ZERO)
					inc = true;
				else if (d < -ZERO)
					dec = true;
				else
					stable = true;
					
				ds.add(stable);
				di.add(inc);
				dd.add(dec);
			}
			
			_booleanMap.put("distance-decreasing"+suffix, dd);
			_booleanMap.put("distance-increasing"+suffix, di);
			_booleanMap.put("distance-stable"+suffix, ds);
		}
	}
	
	/**
	 * For each x,y pair, determine if the agent is moving.  This could be augmented
	 * to additionally have a movement in one of the axes, such as moving-y and 
	 * moving-x
	 *  -- Moving
	 *  
	 *  Note: For now there is no smoothing going on.
	 */
	public void doMoving() { 
		Set<String> entities = new HashSet<String>();
		for (String key : _headers) {
			if (key.startsWith("x(") || key.startsWith("y(")) { 
				entities.add(key.substring(1));
			}
		}
		
		for (String suffix : entities) { 
			List<Double> x = _doubleMap.get("x" + suffix);
			List<Double> y = _doubleMap.get("y" + suffix);
			
			List<Double> diffX = TimeSeries.diff(x);
			List<Double> diffY = TimeSeries.diff(y);

			List<Boolean> moving = new ArrayList<Boolean>();			
			moving.add(false);
			
			for (int i = 1; i < x.size(); ++i) { 
				if (diffX.get(i) > ZERO || diffY.get(i) > ZERO || diffX.get(i) < -ZERO || diffY.get(i) < -ZERO)
					moving.add(true);
				else
					moving.add(false);
			}
		}
	}	
	
	public List<Interval> toIntervals() { 
		List<Interval> intervals = new ArrayList<Interval>();
		for (String key : _booleanMap.keySet()) { 			
			if (key.matches(".*wall.*"))
				continue;

			intervals.addAll(TimeSeries.booleanToIntervals(key, _booleanMap.get(key)));
		}
		return intervals;
	}

	public static void main(String[] args) { 
		WubbleWorld2d ww2d = new WubbleWorld2d();
		String[] activities = {"collide", "pass", "talk-a", "talk-b"};

		for (String act : activities) { 
			try { 
				BufferedWriter out = new BufferedWriter(new FileWriter("data/raw-data/ww2d/" + act + ".lisp"));
				for (int i = 1; i <= 20; ++i) { 
					ww2d.load("data/raw-data/ww2d/" + act + "/" + act + "-" + i + ".csv");
					ww2d.doDistances();
					ww2d.doMoving();
					List<Interval> intervals = ww2d.toIntervals();
					out.write("(" + i + "\n");
					out.write(" (\n");
					for (Interval interval : intervals) { 
						out.write("(\"" + interval.name + "\" " + 
								interval.start + " " +
								interval.end + ")\n");
					}
					out.write(" )\n");
					out.write(")\n");
				}
				out.close();
			} catch (Exception e) { 
				e.printStackTrace();
			}
		}
	}
}
