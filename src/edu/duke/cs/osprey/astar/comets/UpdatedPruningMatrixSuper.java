/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.astar.comets;

import edu.duke.cs.osprey.confspace.ConfSpaceSuper;
import edu.duke.cs.osprey.confspace.HigherTupleFinder;
import edu.duke.cs.osprey.confspace.SuperRCTuple;
import edu.duke.cs.osprey.pruning.PruningMatrix;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 *
 * @author hmn5
 */
public class UpdatedPruningMatrixSuper extends PruningMatrix {

    PruningMatrix parent;//This matrix will have everything pruned in parent, plus updates

    ArrayList<TreeSet<Integer>> prunedRCUpdates = new ArrayList<>();
    //list of pruned RCs (just for this update) at each position

    ArrayList<ArrayList<TreeMap<Integer, TreeSet<Integer>>>> prunedPairUpdates = new ArrayList<>();
    //list of pruned RCs (just for this update) at each position

    public UpdatedPruningMatrixSuper(PruningMatrix parent) {
        this.parent = parent;

        int numPos = parent.numPos();

        for (int pos = 0; pos < numPos; pos++) {
            prunedRCUpdates.add(new TreeSet<Integer>());
            prunedPairUpdates.add(new ArrayList<TreeMap<Integer, TreeSet<Integer>>>());

            for (int pos2 = 0; pos2 < pos; pos2++) {
                prunedPairUpdates.get(pos).add(new TreeMap<Integer, TreeSet<Integer>>());
            }
        }
    }

    @Override
    public void markAsPruned(SuperRCTuple tup) {
        //Store as update
        int tupNumPos = tup.pos.size();

        if (tupNumPos == 1) {
            int pos = tup.pos.get(0);
            int rc = tup.superRCs.get(0);
            prunedRCUpdates.get(pos).add(rc);
        } else if (tupNumPos == 2) {
            int pos1 = tup.pos.get(0);
            int pos2 = tup.pos.get(1);
            int rc1 = tup.superRCs.get(0);
            int rc2 = tup.superRCs.get(1);

            if (pos1 < pos2) {//need to store the pair in descending order of position
                pos2 = tup.pos.get(0);
                pos1 = tup.pos.get(1);
                rc2 = tup.superRCs.get(0);
                rc1 = tup.superRCs.get(1);
            }

            TreeMap<Integer, TreeSet<Integer>> pairs = prunedPairUpdates.get(pos1).get(pos2);

            if (!pairs.containsKey(rc1))//allocate the treeset for pairs involving rc1
            {
                pairs.put(rc1, new TreeSet<Integer>());
            }

            pairs.get(rc1).add(rc2);
        } else {
            throw new RuntimeException("ERROR: UpdatedPruningMatrix just stores updated"
                    + " singles and pairs pruning, can't store pruned tuple: " + tup.stringListing());
        }
    }

    @Override
    public Boolean getPairwise(int res1, int index1, int res2, int index2) {
        //working with residue-specific RC indices directly.  

        if (parent.getPairwise(res1, index1, res2, index2))//first check parent
        {
            return true;
        }

        //also check updates
        if (res1 > res2) {
            return checkIntPair(prunedPairUpdates.get(res1).get(res2), index1, index2);
        } else {
            return checkIntPair(prunedPairUpdates.get(res2).get(res1), index2, index1);
        }
    }

    private static boolean checkIntPair(TreeMap<Integer, TreeSet<Integer>> pairs, int rc1, int rc2) {
        //Check if (rc1, rc2) is in the map (ordered tuple)
        if (pairs.containsKey(rc1)) {
            if (pairs.get(rc1).contains(rc2)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Boolean getOneBody(int res, int index) {

        if (parent.getOneBody(res, index))//first check parent
        {
            return true;
        }

        //also check updates
        return prunedRCUpdates.get(res).contains(index);
    }

    //No updates for higher order
    @Override
    public HigherTupleFinder<Boolean> getHigherOrderTerms(int res1, int index1, int res2, int index2) {
        return parent.getHigherOrderTerms(res1, index1, res2, index2);
    }

    public int countUpdates() {
        //How many update RCs and pairs are there, put together?
        int count = 0;

        for (TreeSet<Integer> posUpdates : prunedRCUpdates) {
            count += posUpdates.size();
        }

        for (ArrayList<TreeMap<Integer, TreeSet<Integer>>> posUpdates : prunedPairUpdates) {
            for (TreeMap<Integer, TreeSet<Integer>> ppUpdates : posUpdates) {
                for (TreeSet<Integer> pUpdates : ppUpdates.values()) {
                    count += pUpdates.size();
                }
            }
        }

        return count;
    }

    @Override
    public int numRCsAtPos(int pos) {
        return parent.numRCsAtPos(pos);
    }

    @Override
    public int numPos() {
        return parent.numPos();
    }
}