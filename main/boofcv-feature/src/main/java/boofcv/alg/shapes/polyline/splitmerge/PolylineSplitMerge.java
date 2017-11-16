/*
 * Copyright (c) 2011-2017, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.shapes.polyline.splitmerge;

import boofcv.misc.CircularIndex;
import georegression.metric.Distance2D_F64;
import georegression.struct.line.LineParametric2D_F64;
import georegression.struct.point.Point2D_I32;
import org.ddogleg.struct.FastQueue;
import org.ddogleg.struct.GrowQueue_I32;
import org.ddogleg.struct.LinkedList;
import org.ddogleg.struct.LinkedList.Element;

import java.util.List;

/**
 * Fits a polyline to a contour by fitting the simplest model and adding more sides to it and then
 * seeing if the lines can be merged together. First it always finds
 * an approximation of a triangle which minimizes the error. A side is added to the current polyline by finding
 * the side that when split will reduce the error by the largest amount. This is repeated until no sides can be
 * split or the maximum number of sides has been reached.
 *
 * TODO redo description
 *
 * TODO Sampling of a side
 *
 * TODO how sides are scored
 *
 *
 *
 * @author Peter Abeles
 */
public class PolylineSplitMerge {
	// todo if flagged as convex only add the corner if it would be convex. that can be done earily if CCW is known
	// TODO add max iterations?
	// todo add max side error?
	// TODO non loop version

	// Can it assume the shape is convex? If so it can reject shapes earlier
	private boolean convex = false;

	// maximum number of sides it will consider
	private int maxSides = 100;

	// The minimum length of a side
	private int minimumSideLength = 10;

	// When selecting the best model how much is a split penalized
	private double cornerScorePenalty = 2;

	// If the score of a side is less than this it is considered a perfect fit and won't be split any more
	private double thresholdSideSplitScore = 1;

	// maximum number of points along a side it will sample when computing a score
	// used to limit computational cost of large contours
	int maxNumberOfSideSamples = 50;

	// work space for side score calculation
	private LineParametric2D_F64 line = new LineParametric2D_F64();

	// the corner list that's being built
	LinkedList<Corner> list = new LinkedList<>();
	FastQueue<Corner> corners = new FastQueue<>(Corner.class,true);

	private SplitSelector splitter = new MaximumLineDistance();
	private SplitResults resultsA = new SplitResults();
	private SplitResults resultsB = new SplitResults();

	// List of all the found polylines and their score
	private FastQueue<CandidatePolyline> polylines = new FastQueue<>(CandidatePolyline.class,true);
	private CandidatePolyline bestPolyline;

	int contourSize;

	public boolean process(List<Point2D_I32> contour ) {
		contourSize = contour.size();

		list.reset();
		corners.reset();
		polylines.reset();
		bestPolyline = null;

		if( contour.size() < 3 )
			return false;

		if( !findInitialTriangle(contour) )
			return false;
		savePolyline();

		for (int numSides = 4; numSides < maxSides; numSides++) {
			if( !increaseNumberOfSidesByOne(contour) )
				break;
			savePolyline();
		}

		bestPolyline = polylines.get(0);
		for (int i = 1; i < polylines.size; i++) {
			if( polylines.get(i).score < bestPolyline.score ) {
				bestPolyline = polylines.get(i);
			}
		}

		return true;
	}

	private void printCurrent() {
		System.out.print("Indexes[");
		Element<Corner> e = list.getHead();
		while( e != null ) {
			System.out.print(" "+e.object.index);
			e = e.next;
		}
		System.out.println(" ]");
	}

	/**
	 * Saves the current polyline
	 */
	void savePolyline() {
		// if a polyline of this size has already been saved then over write it
		CandidatePolyline c;
		if( list.size() <= polylines.size+2 ) {
			c = polylines.get( list.size()-3 );
			// TODO sanity check to see new poly has a better score
		} else {
			c = polylines.grow();
		}

		c.score = computeScore(list,cornerScorePenalty);

		c.splits.reset();
		Element<Corner> e = list.getHead();
		while( e != null ) {
			c.splits.add( e.object.index );
			e = e.next;
		}
	}

	/**
	 * Computes the score for a list
	 */
	static double computeScore( LinkedList<Corner> list , double cornerPenalty ) {
		double sumSides = 0;
		Element<Corner> e = list.getHead();
		while( e != null ) {
			sumSides += e.object.sideError;
			e = e.next;
		}

		return sumSides/list.size() + cornerPenalty*list.size();
	}

	boolean findInitialTriangle(List<Point2D_I32> contour) {
		// find the first estimate for a corner
		int cornerSeed = findCornerSeed(contour);

		// see if it can reject the contour immediately
		if( convex ) {
			if( !sanityCheckConvex(contour,0,cornerSeed))
				return false;
		}

		// Select the second corner.
		splitter.selectSplitPoint(contour,0,cornerSeed,resultsA);
		splitter.selectSplitPoint(contour,cornerSeed,0,resultsB);

		if( splitter.compareScore(resultsA.score,resultsB.score) >= 0 ) {
			addCorner(resultsA.index);
			addCorner(cornerSeed);
		} else {
			addCorner(cornerSeed);
			addCorner(resultsB.index);
		}

		// Select the third corner. Initial triangle will be complete now
		int index0 = list.getHead().object.index;
		int index1 = list.getHead().next.object.index;

		splitter.selectSplitPoint(contour,index1,index0,resultsA);
		addCorner(resultsA.index);

		// TODO recompute the seed corner? maximum distance from some arbitrary point is kinda arbitrary

		// Score each side
		Element<Corner> e = list.getHead();
		while( e != null ) {
			Element<Corner> n = e.next;
			double error;
			if( n == null ) {
				error = computeSideError(contour,e.object.index, list.getHead().object.index);
			} else {
				error = computeSideError(contour,e.object.index, n.object.index);
			}
			e.object.sideError = error;
			e = n;
		}

		// Compute what would happen if a side was split
		e = list.getHead();
		while( e != null ) {
			computePotentialSplitScore(contour,e);
			e = e.next;
		}

		return true;
	}

	Element<Corner> addCorner( int where ) {
		if( where >= contourSize )
			throw new RuntimeException("Egads");
		Corner c = corners.grow();
		c.reset();
		c.index = where;
		list.pushTail(c);
		return list.getTail();
	}

	/**
	 * Increase the number of sides in the polyline. This is done greedily selecting the side which would improve the
	 * score by the most of it was split.
	 * @param contour Contour
	 * @return true if a split was selected and false if not
	 */
	boolean increaseNumberOfSidesByOne(List<Point2D_I32> contour) {
		Element<Corner> selected = selectCornerToSplit();

		// No side can be split
		if( selected == null )
			return false;

		// Update the corner who's side was just split
		selected.object.sideError = selected.object.splitError0;
		// split the selected side and add a new corner
		Corner c = corners.grow();
		c.reset();
		c.index = selected.object.splitLocation;
		c.sideError = selected.object.splitError1;
		Element<Corner> cornerE = list.insertAfter(selected,c);

		if( c.index >= contour.size() )
			throw new RuntimeException("Egads");

		// compute the score for sides which just changed
		computePotentialSplitScore(contour,next(cornerE));
		computePotentialSplitScore(contour,previous(cornerE));

		// Save the results
		savePolyline();

		// See if new lines formed by split should be merged together with old adjacent lines
		considerRemovingAndRemove(contour,next(cornerE));
		considerRemovingAndRemove(contour,previous(cornerE));

		return true;
	}

	/**
	 * Selects the best side to split the polyline at.
	 * @return the selected side or null if the score will not be improved if any of the sides are split
	 */
	Element<Corner> selectCornerToSplit() {
		Element<Corner> selected = null;
		double bestChange = 0;

		// Pick the side that if split would improve the overall score the most
		Element<Corner> e = list.getHead();
		while( e != null ) {
			Corner c = e.object;
			if( !c.splitable) {
				e = e.next;
				continue;
			}

			// compute how much better the score will improve because of the split
			double change = c.sideError -(c.splitError0 +c.splitError1)/2.0;
			if( change > bestChange ) {
				bestChange = change;
				selected = e;
			}
			e = e.next;
		}

		return selected;
	}

	/**
	 * See if removing a corner will improve the overall score. If it is an improvement then the corner is removed,
	 * merging two sides.
	 * @param contour Shape's contour
	 * @param target The corner which is to be removed
	 */
	void considerRemovingAndRemove(List<Point2D_I32> contour, Element<Corner> target ) {
		if( list.size() <= 3 )
			return;

		Element<Corner> p = previous(target);
		Element<Corner> n = next(target);

		// just contributions of the corners in question
		double before = (p.object.sideError +target.object.sideError)/list.size() + cornerScorePenalty;
		double sideScoreNew = computeSideError(contour,p.object.index,n.object.index);
		double after = sideScoreNew/(list.size()-1);

		// See if the new shape has a better score. if so save the results
		if( after < before ) {
			// Note: the corner is "lost" until the next contour is fit. Not worth the effort to recycle
			p.object.splitLocation = target.object.index;
			p.object.splitError0 = target.object.sideError;
			p.object.splitError1 = n.object.sideError;
			p.object.sideError = sideScoreNew;

			if( p.object.splitLocation >= contour.size() )
				throw new RuntimeException("Egads");

			list.remove(target);
			savePolyline();
		}
	}

	/**
	 * The seed corner is the point farther away from the first point. In a perfect polygon with no noise this should
	 * be a corner.
	 */
	static int findCornerSeed(List<Point2D_I32> contour ) {
		Point2D_I32 a = contour.get(0);

		int best = -1;
		double bestDistance = -Double.MAX_VALUE;

		for (int i = 1; i < contour.size(); i++) {
			Point2D_I32 b = contour.get(i);

			double d = distanceSq(a,b);
			if( d > bestDistance ) {
				bestDistance = d;
				best = i;
			}
		}

		return best;
	}

	/**
	 * Scores a side based on the sum of Euclidean distance squared of each point along the line. Euclidean squared
	 * is used because its fast to compute
	 *
	 * @param indexA first index. Inclusive
	 * @param indexA last index. Exclusive
	 */
	double computeSideError(List<Point2D_I32> contour , int indexA , int indexB ) {
		assignLine(contour, indexA, indexB, line);

		// don't sample the end points because the error will be zero by definition
		int numSamples;
		double sumOfDistances = 0;
		if( indexB >= indexA ) {
			int length = indexB-indexA-1;
			numSamples = Math.min(length,maxNumberOfSideSamples);
			for (int i = 0; i < numSamples; i++) {
				int index = indexA+1+length*i/numSamples;
				Point2D_I32 p = contour.get(index);
				sumOfDistances += Distance2D_F64.distanceSq(line,p.x,p.y);
			}
			sumOfDistances /= (numSamples+1); // add back the two end point samples
		} else {
			int length = contour.size()-indexA-1 + indexB;
			numSamples = Math.min(length,maxNumberOfSideSamples);
			for (int i = 0; i < numSamples; i++) {
				int where = length*i/numSamples;
				int index = (indexA+1+where)%contour.size();
				Point2D_I32 p = contour.get(index);
				sumOfDistances += Distance2D_F64.distanceSq(line,p.x,p.y);
			}
			sumOfDistances /= (numSamples+1);
		}

		return sumOfDistances;
	}

	/**
	 * Computes the split location and the score of the two new sides if it's split there
	 */
	void computePotentialSplitScore( List<Point2D_I32> contour , Element<Corner> e0 )
	{
		Element<Corner> e1 = next(e0);

		e0.object.splitable = canBeSplit(contour.size(),e0);

		if( e0.object.splitable ) {
			setSplitVariables(contour, e0, e1);
		}
	}

	void setSplitVariables(List<Point2D_I32> contour, Element<Corner> e0, Element<Corner> e1) {
		splitter.selectSplitPoint(contour, e0.object.index, e1.object.index, resultsA);
		e0.object.splitLocation = resultsA.index;
		e0.object.splitError0 = computeSideError(contour, e0.object.index, resultsA.index);
		e0.object.splitError1 = computeSideError(contour, resultsA.index, e1.object.index);

		if( e0.object.splitLocation >= contour.size() )
			throw new RuntimeException("Egads");
	}

	/**
	 * Determines if the side can be split again. A side can always be split as long as
	 * its >= the minimum length or that the side score is larger the the split threshold
	 */
	boolean canBeSplit( int contourSize , Element<Corner> e0 ) {
		Element<Corner> e1 = next(e0);

		int length = CircularIndex.distanceP(e0.object.index,e1.object.index,contourSize);
		if( length < minimumSideLength ) {
			return false;
		}

		return !(e0.object.sideError < thresholdSideSplitScore);
	}

	/**
	 * Returns the next corner in the list
	 */
	Element<Corner> next( Element<Corner> e ) {
		if( e.next == null ) {
			return list.getHead();
		} else {
			return e.next;
		}
	}

	/**
	 * Returns the previous corner in the list
	 */
	Element<Corner> previous( Element<Corner> e ) {
		if( e.previous == null ) {
			return list.getTail();
		} else {
			return e.previous;
		}
	}

	/**
	 * For convex shapes no point along the contour can be farther away from A is from B. Thus the maximum number
	 * of points can't exceed a 1/2 circle.
	 *
	 * NOTE: indexA is probably the top left point in the contour, since that's how most contour algorithm scan
	 * but this isn't known for sure. If it was known you could make this requirement tighter.
	 *
	 * @param contour Contour points
	 * @param indexA index of first point
	 * @param indexB index of second point
	 * @return if it passes the sanity check
	 */
	static boolean sanityCheckConvex( List<Point2D_I32> contour , int indexA , int indexB )
	{
		double d = Math.sqrt(distanceSq(contour.get(indexA),contour.get(indexB)));

		int maxAllowed = (int)(2*Math.PI*d+0.5);

		if( indexA > indexB ) {
			int tmp = indexA;
			indexA = indexB;
			indexB = tmp;
		}
		if( indexB-indexA > maxAllowed )
			return false;
		if( indexA + contour.size()-indexB > maxAllowed )
			return false;

		return true;
	}

	/**
	 * Using double prevision here instead of int due to fear of overflow in very large images
	 */
	static double distanceSq( Point2D_I32 a , Point2D_I32 b ) {
		double dx = b.x-a.x;
		double dy = b.y-a.y;

		return dx*dx + dy*dy;
	}

	/**
	 * Assigns the line so that it passes through points A and B.
	 */
	public static void assignLine(List<Point2D_I32> contour, int indexA, int indexB, LineParametric2D_F64 line) {
		Point2D_I32 endA = contour.get(indexA);
		Point2D_I32 endB = contour.get(indexB);

		line.p.x = endA.x;
		line.p.y = endA.y;
		line.slope.x = endB.x-endA.x;
		line.slope.y = endB.y-endA.y;
	}

	public FastQueue<CandidatePolyline> getPolylines() {
		return polylines;
	}

	public CandidatePolyline getBestPolyline() {
		return bestPolyline;
	}

	/**
	 * Storage for results from selecting where to split a line
	 */
	static class SplitResults
	{
		public int index;
		public double score;
	}

	/**
	 * Corner in the polyline. The side that this represents is this corner and the next in the list
	 */
	public static class Corner
	{
		public int index;
		public double sideError;
		// if this side was to be split this is where it would be split and what the scores
		// for the new sides would be
		public int splitLocation;
		public double splitError0, splitError1;

		// if a side can't be split (e.g. too small or already perfect)
		public boolean splitable;

		public void reset() {
			index = -1;
			sideError = -1;
			splitLocation = -1;
			splitError0 = splitError1 = -1;
			splitable = true;
		}
	}

	public static class CandidatePolyline
	{
		public GrowQueue_I32 splits = new GrowQueue_I32();
		public double score;
	}

	public boolean isConvex() {
		return convex;
	}

	public void setConvex(boolean convex) {
		this.convex = convex;
	}

	public int getMaxSides() {
		return maxSides;
	}

	public void setMaxSides(int maxSides) {
		this.maxSides = maxSides;
	}

	public int getMinimumSideLength() {
		return minimumSideLength;
	}

	public void setMinimumSideLength(int minimumSideLength) {
		this.minimumSideLength = minimumSideLength;
	}

	public double getCornerScorePenalty() {
		return cornerScorePenalty;
	}

	public void setCornerScorePenalty(double cornerScorePenalty) {
		this.cornerScorePenalty = cornerScorePenalty;
	}

	public double getThresholdSideSplitScore() {
		return thresholdSideSplitScore;
	}

	public void setThresholdSideSplitScore(double thresholdSideSplitScore) {
		this.thresholdSideSplitScore = thresholdSideSplitScore;
	}

	public int getMaxNumberOfSideSamples() {
		return maxNumberOfSideSamples;
	}

	public void setMaxNumberOfSideSamples(int maxNumberOfSideSamples) {
		this.maxNumberOfSideSamples = maxNumberOfSideSamples;
	}

	public void setSplitter(SplitSelector splitter) {
		this.splitter = splitter;
	}
}

