/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.tupexp;

import cern.colt.matrix.DoubleFactory1D;
import cern.colt.matrix.DoubleMatrix1D;
import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.ematrix.EnergyMatrix;
import edu.duke.cs.osprey.tools.ObjectIO;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

/**
 *
 * @author mhall44
 */
/**
 *
 * Create an energy matrix that approximates a function of many integers (e.g., list of RCs for all
 * flexible residues) as a sum of terms dependent on low-order tuples (e.g., tuple
 * of RCs at a few positions)
 * 
 * 
 * @author mhall44
 */

public abstract class TupleExpander implements Serializable {
    
    int numPos;//number of positions
    int numAllowed[];//number of possible assignments at each position
    
    ArrayList<RCTuple> tuples = new ArrayList<>();//the tuples we're trying to expand in
    
    double[] tupleTerms;//terms for the tuples
    double constTerm = Double.NaN;
    //so we approximate our function as constTerm + sum_{tup in current tuples} tupleTerms[tup]
    
    double bCutoff;
    double bCutoff2;
    
    boolean subCutoffSamplesOnly = true;//only fit to sub-cutoff samples (basic least squares)
    //justification: in the discrete-fit setting here, we need to be able to quantitatively determine
    //all non-pruned tuples' coeffs from the sub-cutoff region
    //so we can get the effect of modified least squares by pruning tuples not found in sub-cutoff region
    //and then fitting coeffs that are found there
    
    TESampleSet trainingSamples=null, CVSamples=null;
    
    
    double pruningInterval;//what pruning interval is this expansion valid up to?
       
    
    static int printedUpdateNumTuples = 5;//100 w/o PB.  We print updates as we add tuples...this is how often
    
    public TupleExpander (int numPos, int[] numAllowed/*, double constTerm*/, double pruningInterval) {
        this.numPos = numPos;
        this.numAllowed = numAllowed;
        //this.constTerm = constTerm;
        this.pruningInterval = pruningInterval;
        
        for(int pos=0; pos<numPos; pos++){
            /*tuplesForAssignments.add(new ArrayList<ArrayList<Integer>>());
            for(int a=0; a<numAllowed[pos]; a++)
                tuplesForAssignments.get(pos).add(new ArrayList<Integer>());*/
            
            assignmentSets.add(new ArrayList<ArrayList<Integer>>());
        }
    }
    
    
    
    
    
    double computeInitGMECEst(){//let's find this by some random iterations...
        //double ans = 0;//ASSUMING WILL BE <0 DEBUG!!!
        double ans = Double.POSITIVE_INFINITY;
        TESampleSet tss = new TESampleSet(this);
        
        System.out.println("Computing initial GMEC estimate...");
        
        //DEBUG!!
        for(int iter=0; iter<500/*0*/; iter++){
            int sample[] = new int[numPos];
            boolean success;
            do {
                Arrays.fill(sample,-1);
                success = tss.finishSample(sample);
            } while (!success);
            
            double score = scoreAssignmentList(sample);
            ans = Math.min(ans,score);
        }
        
        return ans;
    }
    
    
    public double calcExpansion(ArrayList<RCTuple> tuplesToFit){
        //calculate the tuple coefficients
        //return cross-validation total residual
        
        
        //DEBUG!!!
        //this was used to access these things from Main in 2O9S debug
        /*for(int s=0; s<trainingSamples.samples.size(); s++){
            int[] samp = trainingSamples.samples.get(s);
            double trueVal = trainingSamples.trueVals.get(s);
            double fitVal = trainingSamples.curFitVals.get(s);
            for(int rc : samp)
                System.out.print(rc+" ");
            System.out.println(trueVal+" "+fitVal);
        }
        System.exit(0);*/
        
        
        if(Double.isNaN(constTerm))//constTerm not computed yet
            constTerm = computeInitGMECEst();
        //bCutoff = constTerm+5;//DEBUG!!
        bCutoff = constTerm+20;
        bCutoff2 = constTerm+50;
        
        
        //DEBUG!!!
        bCutoff = Double.POSITIVE_INFINITY;
        bCutoff2 = Double.POSITIVE_INFINITY;
        
        //this can be used for the first tuple expansion for this object, or to add tuples later (not take away though)
        
        System.out.println("About to calculate tuple expansion with "+tuplesToFit.size()+" tuples.");
        System.out.println("constTerm: "+constTerm+" bCutoff: "+bCutoff+" bCutoff2: "+bCutoff2);

        
        setupSamples(tuplesToFit);//set up the training set (in the process, prune tuples that don't provide reasonable energies)
                
        fitLeastSquares();
        
        trainingSamples.updateFitVals();
        CVSamples.updateFitVals();
        
        System.out.println("TRAINING SAMPLES: ");
        trainingSamples.printResids();
        
        System.out.println("CV SAMPLES: ");
        CVSamples.printResids();
        
        

        //DEBUG!!!!
        /*System.out.println("OUTPUTTING TUPLE EXPANDER");
        ObjectIO.writeObject(this, "TUPLE_EXPANDER.dat");
        System.exit(0);
        */
        
        
        //DEBUG!!!!
        //to check on GMEC energy for 1CC8 system
        /*
        int conf2[] = new int[] {5,7,12,5,0,7,4};
        boolean b2 = isPruned(new RCTuple(conf2));
        ArrayList<Integer> sampleTuples2 = trainingSamples.calcSampleTuples(conf2);
        ArrayList<Integer> sampleTuples2CV = CVSamples.calcSampleTuples(conf2);
        double E2 = fitValueForTuples(sampleTuples2);
        double EPIC2 = scoreAssignmentList(conf2);
        int aaa = 0;
        */
        
        
        
        return CVSamples.totalResid;
    }
    
    //DEBUG!!
    int ezPruneCount = 0;
    
    void setupSamples(ArrayList<RCTuple> tuplesToFit){
        //generate the training sample set, and for each tuple we want to fit, either prune it or get samples for it
        
        
        
        //137DEBUG!!!!!
        boolean targetRun = (tuplesToFit.size() == 115793);
        if(targetRun)
            System.out.print("TARGET RUN");
        
        
        if(trainingSamples==null)//first tuple expansion for this object: initialize training samples
            trainingSamples = new TESampleSet(this);
        else {
            //figure out which tuples are already included in this expansion, so we don't try to add them again
            //note: assuming any such redundant tuples have the same ordering of positions!
            for(int tupNum=tuplesToFit.size()-1; tupNum>=0; tupNum--){
                RCTuple tup = tuplesToFit.get(tupNum);
                boolean removeTuple = false;
                
                for(RCTuple tupHere : tuples){
                    if(tupHere.isSameTuple(tup)){
                        tuplesToFit.remove(tupNum);
                        removeTuple = true;
                        break;
                    }
                }
                
                if(!removeTuple) {
                    //also want to remove any redundant tuples among the new ones...  (same assumption about ordering)
                    for(int tupNum2=0; tupNum2<tupNum; tupNum2++){
                        if( tup.isSameTuple( tuplesToFit.get(tupNum2) ) ){
                            tuplesToFit.remove(tupNum);
                            break;
                        }
                    }
                }
            }
        }
        
        
        System.out.println("Adding tuples to expansion and drawing training samples...");
        
        for(int tupNum=0; tupNum<tuplesToFit.size(); tupNum++){
            if(tupNum>0 && (tupNum%printedUpdateNumTuples==0))
                System.out.println(tupNum+" tuples added");
            
            
            
            //137DEBUG!!!
            /*if(tupNum>=1200 && targetRun){
                
                //want to see what's taking up the memory.  Look at size of tuple expander and components.  
                for( String stype : new String[] {"training","CV"} ){
                    TESampleSet tss = trainingSamples;
                    if(stype.equalsIgnoreCase("CV"))
                        tss = CVSamples;
                    
                    System.out.println("Dumping "+stype+" sampleTuples");
                    ObjectIO.writeObject(tss.sampleTuples, stype+".sampleTuples.dat");
                    
                    System.out.println("Dumping "+stype+" tupleSamples");
                    ObjectIO.writeObject(tss.tupleSamples, stype+".tupleSamples.dat");
                    
                    System.out.println("Dumping "+stype+" tupleSamplesAboveCutoff");
                    ObjectIO.writeObject(tss.tupleSamplesAboveCutoff, stype+".tupleSamplesAboveCutoff.dat");
                    
                    System.out.println("Dumping full "+stype+" sample set");
                    ObjectIO.writeObject(tss, stype+".full.dat");
                }
                
                System.out.println("Dumping tuple expander to TUP_EXP_TARGETRUN.dat");
                ObjectIO.writeObject(this, "TUP_EXP_TARGETRUN.dat");
                //System.exit(0);
            }*/
            
            
            
            tryAddingTuple(tuplesToFit.get(tupNum));//prune or get samples
        }
        
        
        System.out.println("EZPRUNE COUNT: "+ezPruneCount);
        
        System.out.println("Updating samples to finish training set...");
        
        for(int t=0; t<tuples.size(); t++)
            //replace any samples that were removed during pruning
            trainingSamples.updateSamples(t);
        
        
        //let's try to get about >=2x as many samples as tuples, to avoid overfitting
        //increase number of samples per tuple to achieve this
        if(trainingSamples.samples.size() < 2*tuples.size()){
            numSampsPerTuple *= 2*tuples.size()/trainingSamples.samples.size()+1;//+1 to round up
            
            for(int t=0; t<tuples.size(); t++)//get the additional samples we need
                trainingSamples.updateSamples(t);
        }
        
        System.out.println("Training set done.");
        System.out.println("Drawing CV samples.");
        
        //now make sure the CV samples are updated too
        if(CVSamples==null){
            CVSamples = new TESampleSet(this);
            for(int t=0; t<tuples.size(); t++)
                CVSamples.updateSamples(t);
        }
            
        System.out.println("CV set done.");
    }
    
    
    
    
    
    void fitLeastSquares(){
        //set up to call fitSeriesIterative
        int numTrainingSamples = trainingSamples.samples.size();
        
        /*DoubleMatrix1D samp[] = new DoubleMatrix1D[numTrainingSamples];//just what terms are active at each sample
        
        for(int s=0; s<numTrainingSamples; s++){
            samp[s] = DoubleFactory1D.dense.make(tuples.size());
            for(int t=0; t<tuples.size(); t++){
                if(trainingSamples.sampleTuples.get(s).contains(t))
                    samp[s].set(t,1);
            }
        }*/
        
        double[] trueVals = new double[numTrainingSamples];
        
        for(int s=0; s<numTrainingSamples; s++){
            trueVals[s] = trainingSamples.trueVals.get(s) - constTerm;
        }
        
        //weights, bcutoffs (even bcutoffs2) may be needed but hopefully not (if so modify CG: full iterative quite expensive)
        /*double fitTerms[] = SeriesFitter.fitSeriesIterative(samp, trueVals, weights, lambda, false, 1,
                    bCutoffs, bCutoffs2, 1, null);*/
        
        
        if(!subCutoffSamplesOnly)
            throw new RuntimeException("ERROR: CG fitting doesn't support modified least squares at this time");
        
        //DEBUG!!  Adding regularization...
        //RegTupleFitter fitter = new RegTupleFitter(trainingSamples.sampleTuples,tuples.size(),trueVals);
        /*ArrayList<ArrayList<Integer>> sampleTuples = new ArrayList<>();
        for(int[] sample : trainingSamples.samples)
            sampleTuples.add(trainingSamples.calcSampleTuples(sample));
        RegTupleFitter fitter = new RegTupleFitter(sampleTuples,tuples.size(),trueVals);*/
        
        //RegTupleFitter2 fitter = new RegTupleFitter2(trainingSamples, tuples.size(), trueVals);
        TupleIndexMatrix tim = getTupleIndexMatrix();
        CGTupleFitter2 fitter = new CGTupleFitter2(tim, trainingSamples.samples, tuples.size(), trueVals);
        
        
        
        
        //DEBUG!!!  Addressing infinite residuals for 40-residue sidechain placement
        /*System.out.println("Preparing to fit tuples.  Const term: "+constTerm+" Num tuples: "+tuples.size());
        System.out.println("Writing to true vals to TRUEVALS.dat and samplesTuples to SAMPLETUPLES.dat");
        
        ObjectIO.writeObject(trueVals, "TRUEVALS.dat");
        ObjectIO.writeObject(trainingSamples.sampleTuples, "SAMPLETUPLES.dat");
        
        for(int s=0; s<numTrainingSamples; s++){
            if(Double.isInfinite(trueVals[s])){
                System.out.println("INFINITE TRUE VAL: "+trueVals[s]);
                
                int conf[] = trainingSamples.samples.get(s);
                System.out.print("conf: ");
                for(int c : conf)
                    System.out.print(c+" ");
                System.out.println();
                
                System.out.println("Conf pruned: "+isPruned(new RCTuple(conf)));
                
                System.out.println("Score for conf: "+scoreAssignmentList(conf));
                
                ConfETupleExpander cete = (ConfETupleExpander)this;
                System.out.println( "Flat E: " + cete.sp.emat.confE(conf) );
                System.out.println( "EPIC E: " + cete.sp.epicMat.minContE(conf) );
                
                System.out.println("Outputting EPIC energy function for conf");
                ObjectIO.writeObject(cete.sp.epicMat.internalEnergyFunction(new RCTuple(conf)), "EPICEFUNC_inf.dat");
                System.exit(0);
            }
        }*/
        
        
        
        double fitTerms[] = fitter.doFit();
        tupleTerms = fitTerms;
        
        
        
        
        //DEBUG!!! still 40-res issue
        /*
        System.out.println("CHECKING FOR INFINITE VALUES AFTER FIT");
        for(int s=0; s<numTrainingSamples; s++){
            if(Double.isInfinite(trueVals[s])){
                System.out.println("INFINITE TRUE VAL: "+trueVals[s]);
            }
        }
        
        for(int t=0; t<tuples.size(); t++){
            if(Double.isInfinite(tupleTerms[t])){
                System.out.println("INFINITE TUPLE TERM: "+tupleTerms[t]);
                System.out.print("Tuple: "+tuples.get(t).stringListing());
            }
        }
        System.out.println("DONE CHECKING FOR INFINITE VALUES AFTER FIT");
        */
        
        
    }
    
    
    
    
    
    
    int numSampsPerTuple = 10;
    
    int numSamplesNeeded(int tup){
        //for a tuple (index in tuples), how many samples are needed?
        return numSampsPerTuple;//one param per tuple, so 10 samples should securely avoid overfitting
    }
    
    
    /*void addTuple(int[][] tup, boolean updateSamples){
        tuples.add(tup);
        int newTupleIndex = tuples.size()-1;//tup index in tuples
        
        for(int[] op : tup){
            //add index in tuples of tup to tuplesForAssignments
            if(op[1]>=0)
                tuplesForAssignments.get(op[0]).get(op[1]).add(newTupleIndex);
        }
        
        if(useTupleBCutoffs)
            tupleBCutoffs.add(bCutoff);
        
        if(updateSamples){//sample sets already drawn, need to update
            trainingSamples.addTuple(newTupleIndex);
            CVSamples.addTuple(newTupleIndex);
        }
    }*/
    
    void tryAddingTuple(RCTuple tup){
        //We assume here that the training set has been created
        //and we are going to add the tuple if it has sub-bcutoff samples
        //DEBUG!!! we currently just try drawing a while (tupleFeasible()) 
        //and declare it impossible upon failure,
        //but later will need to pull out more rigorous pruning methods 
        //to exclude tuples for which we find tupleFeasible==false
        
        if(trainingSamples.tupleFeasible(tup)){

            tuples.add(tup);
            int newTupleIndex = tuples.size()-1;//tup index in tuples

            /*for(int[] op : tup){
                //add index in tuples of tup to tuplesForAssignments
                if(op[1]>=0)
                    tuplesForAssignments.get(op[0]).get(op[1]).add(newTupleIndex);
            }

            if(useTupleBCutoffs)
                tupleBCutoffs.add(bCutoff);*/

            trainingSamples.addTuple(newTupleIndex);
            if(CVSamples!=null)
                CVSamples.addTuple(newTupleIndex);
        }
        else {
            
            //mark as pruned
            pruneTuple(tup);

            //DEBUG!!
            ezPruneCount++;
        }
    }
    
    
    /*void pruneTuple(int[][] tup){
        //Mark the tuple is pruned, so we exclude it from the conformational space
        //represented by this tuple expansion
        //also remove previously drawn samples that include it
        
        if(tup.length==1)
            throw new RuntimeException("ERROR: On-the-fly singles pruning not supported currently");
        else if(tup.length==2)
            prunePair(tup[0][0],tup[0][1],tup[1][0],tup[1][1]);
        else
            higherOrderPrunedTuples.add(tup);
        
        if(trainingSamples!=null)
            ((TESampleSet2)trainingSamples).removeSamplesWithTuple(tup);
        if(CVSamples!=null)
            ((TESampleSet2)CVSamples).removeSamplesWithTuple(tup);
    }*/
    
    
    
    
    
    double fitValueForTuples(ArrayList<Integer> tuples){
        //given a list of tuples to which a term belongs
        //return the fit value
        double ans = constTerm;
        for(int tup : tuples)
            ans += tupleTerms[tup];
        return ans;
    }
    
    void updateSampBelowCutoffOtherSet(TESampleSet callingSet){
        //if when drawing samples we need to raise the bCutoff
        //we need to update all sample set(s)'s tupleSamplesAboveCutoff fields
        //this will probably be called when drawing either training or CV samples
        //and will be used to update the other one
        if(trainingSamples!=null && trainingSamples!=callingSet)
            trainingSamples.updateSampBelowCutoff();
        if(CVSamples!=null && CVSamples!=callingSet)
            CVSamples.updateSampBelowCutoff();
    }
    
    
    
    
    //HANDLING OF ASSIGNMENT SETS
    //These will be specified by negative values in a tuple array
    //Value -i for residue res denotes the set of assignments indexed by i in assignmentSets.get(res)
    ArrayList<ArrayList<ArrayList<Integer>>> assignmentSets = new ArrayList<>();
    
    
    int getAssignmentSet(int res, ArrayList<Integer> assignmentList){
        Collections.sort(assignmentList);
        
        ArrayList<ArrayList<Integer>> resASets = assignmentSets.get(res);
        
        for(int i=0; i<resASets.size(); i++){
            if(resASets.get(i).size() == assignmentList.size()){
                boolean listsEqual = true;
                for(int j=0; j<assignmentList.size(); j++){
                    if(resASets.get(i).get(j) != assignmentList.get(j))
                        listsEqual = false;
                }
            
                if(listsEqual)
                    return i;//found a match for this assignment set
            }
        }
        
        //if we get here, the assignment set is not currently listed, so we must do so
        resASets.add(assignmentList);
        return resASets.size() - 1;
    }
    
    
    void assignTupleInSample(int sample[], RCTuple tuple){
        //assign the sample to have the assignments specified by tuple
        //if there are assignment sets, pick randomly, though avoid pruned pairs
        for(int posCount=0; posCount<tuple.pos.size(); posCount++){
            
            int pos = tuple.pos.get(posCount);
            int rc = tuple.RCs.get(posCount);
            
            if(rc>=0)//specific assignments at position op[0]
                sample[pos] = rc;
            else{//assignment set...draw randomly
                ArrayList<Integer> aSet = assignmentSets.get(pos).get(-rc);
                int assignment = aSet.get( new Random().nextInt(aSet.size()) );
                
                while(!checkAssignmentUnpruned(sample,pos,assignment)){
                    //looks like this assignment is incompatible with rest of sample assigned so far
                    //redraw from rest of possible assignments...
                    ArrayList<Integer> aSetRed = new ArrayList<>();
                    for(int a : aSet){
                        if(a!=assignment)
                            aSetRed.add(a);
                    }
                    
                    aSet = aSetRed;
                    
                    if(aSet.isEmpty())
                        throw new RuntimeException("ERROR: Can't find compatible assignment for tuple in sample...");
                        //let's currently address this as an error...
                    
                    assignment = aSet.get( new Random().nextInt(aSet.size()) );
                }
                
                sample[pos] = assignment;
            }
        }
    }
    
    
    boolean checkAssignmentUnpruned(int sample[], int pos, int assignment){
        //given a partially defined sample (-1s for unassigned positions)
        //see if the given assignment at position pos is compatible pairs pruning-wise
        for(int pos2=0; pos2<sample.length; pos2++){
            
            RCTuple pair = new RCTuple(pos,assignment,pos2,sample[pos2]);
            
            if(sample[pos2]!=-1){
                if(isPruned(pair))
                    return false;
            }
        
            for(RCTuple prunedTup : higherOrderPrunedTuples(pair)){
                if(sampleMatchesTuple(sample,prunedTup))
                    return false;
            }
        }
                    
        //if we get here, we're unpruned
        return true;
    }
    
    
    
    boolean sampleMatchesTuple(int sample[], RCTuple tup){
        boolean termApplies = true;

        for(int posNum=0; posNum<tup.pos.size(); posNum++){
            
            int pos = tup.pos.get(posNum);
            int rc = tup.RCs.get(posNum);
            
            if(sample[pos]==-1)//undefined considered not to match
                return false;
            
            if(rc>=0){//sample must match specific assignment in tuple
                if( sample[pos] != rc ){
                    termApplies = false;
                    break;
                }
            }
            else {//sample must be in assignment set in tuple
                ArrayList<Integer> aSet = assignmentSets.get(pos).get(-rc);
                if( ! aSet.contains(sample[pos]) ){
                    termApplies = false;
                    break;
                }
            }
        }

        return termApplies;
    }
    
    
    public EnergyMatrix getEnergyMatrix(){
        //put the tuples into an energy matrix and return it
        EnergyMatrix ans = new EnergyMatrix(numPos,numAllowed,pruningInterval);
        
        ans.setConstTerm(constTerm);
        

        //first, let's put in values not explicitly in the tuple expansion
        //the tuple-expansion energy is the sum of values for all tuples in a conf
        //but an energy matrix expects values for all one-body and pairwise energies
        //so we'll specify these to be 0 for RCs and pairs not in the expansion.
        //HOWEVER, pruned tuples must be marked as impossible, i.e. infinite.  
        for(int pos=0; pos<numPos; pos++){
            for(int rc=0; rc<numAllowed[pos]; rc++){
                
                if(isPruned(new RCTuple(pos,rc)))
                    ans.setOneBody(pos, rc, Double.POSITIVE_INFINITY);
                else
                    ans.setOneBody(pos, rc, 0.);
                
                for(int pos2=0; pos2<pos; pos2++){
                    for(int rc2=0; rc2<numAllowed[pos2]; rc2++){
                        RCTuple pair = new RCTuple(pos,rc,pos2,rc2);
                        if(isPruned(pair))
                            ans.setPairwise(pos, rc, pos2, rc2, Double.POSITIVE_INFINITY);
                        else
                            ans.setPairwise(pos, rc, pos2, rc2, 0.);
                        
                        //higher tuples 0 by default...just mark pruned ones
                        for(RCTuple prunedTup : higherOrderPrunedTuples(pair))
                            ans.setTupleValue(prunedTup, Double.POSITIVE_INFINITY);
                    }
                }
                
            }
        }
        
        
        for(int tupNum=0; tupNum<tuples.size(); tupNum++){
            ans.setTupleValue( tuples.get(tupNum), tupleTerms[tupNum] );
        }
        
        return ans;
    }
    
    
    
    public TupleIndexMatrix getTupleIndexMatrix(){
        //Make a matrix of the indices in tuples of each tuple
        TupleIndexMatrix ans = new TupleIndexMatrix(numPos, numAllowed, pruningInterval);
        
        //first, fill in -1's for one-body and tuple terms, since values are expected at these matrix entries
        //-1 indicates absence of a tuple in the expansion, and will be overwritten as needed
        for(int pos=0; pos<numPos; pos++){
            for(int rc=0; rc<numAllowed[pos]; rc++){
                
                ans.setOneBody(pos, rc, -1);
                
                for(int pos2=0; pos2<pos; pos2++){
                    for(int rc2=0; rc2<numAllowed[pos2]; rc2++){
                        ans.setPairwise(pos, rc, pos2, rc2, -1);
                    }
                }
            }
        }
        
        
        for(int tupNum=0; tupNum<tuples.size(); tupNum++)
            ans.setTupleValue( tuples.get(tupNum), tupNum );
        
        return ans;
    }
    
    
    //functions dependent on what the assignments mean, etc.
    
    //score a list of assignments for each position
    abstract double scoreAssignmentList(int[] assignmentList);
    
    //prune, or check pruning of, a tuple
    abstract boolean isPruned(RCTuple tup);
    abstract void pruneTuple(RCTuple tup);
    
    
    //list higher-order pruned tuples that include the specified pair
    abstract ArrayList<RCTuple> higherOrderPrunedTuples(RCTuple tup);
    
    

    

    
}
