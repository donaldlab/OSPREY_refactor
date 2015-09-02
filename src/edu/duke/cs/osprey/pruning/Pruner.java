/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.pruning;

import edu.duke.cs.osprey.confspace.HigherTupleFinder;
import edu.duke.cs.osprey.confspace.SearchProblem;
import edu.duke.cs.osprey.confspace.RC;
import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.confspace.TupleEnumerator;
import edu.duke.cs.osprey.ematrix.EnergyMatrix;
import edu.duke.cs.osprey.ematrix.epic.EPICMatrix;
import edu.duke.cs.osprey.pruning.PruningMethod.CheckSumType;
import java.util.ArrayList;
import java.util.TreeSet;

/**
 *
 * @author mhall44
 */
public class Pruner {
    //This representation based on "candidate" and "competitor" objects and iterators
    //will let us cut down a lot on the code redundancy
    //and will make it way easier to update things when we need to
    //"DEECandidate", etc. can represent either a rotamer or a pair (or a triple?)
    //the candidate, competitor, and witness iterators can be reused between most types of Pruner
    //(usually we just need an iterator over rotamers, or an iterator over pairs)
    //and then each type of Pruner has its own pruning condition
    
    SearchProblem searchSpace;
    
    boolean typeDep;//use type-dependent pruning
    double boundsThreshold;//any conformations above this threshold are liable to Bounds pruning
    
    double pruningInterval;//any conformations more than Ew above optimal are liable to competitive pruning
    //(this includes I in iMinDEE, since in this case we are using the pairwise-minimal energies
    //and "optimal" refers to the lowest pairwise bound)
    
    EnergyMatrix emat;//energy matrix to prune based on
    
    TupleEnumerator tupEnum;//use to enumerate candidate RC tuples, etc.
    
    boolean useEPIC;//Use EPIC in pruning (to lower bound continuous E of candidate)
    
    static int triplesNumPartners = 5;//2//let's do sparse triples, considering like 5 interactions per pos to be strong
    
    
    public Pruner(SearchProblem searchSpace, boolean typeDep, double boundsThreshold, 
            double pruningInterval, boolean useEPIC, boolean useTupExp) {
        
        this.searchSpace = searchSpace; 
        this.typeDep = typeDep;
        this.boundsThreshold = boundsThreshold;
        this.pruningInterval = pruningInterval;
        this.useEPIC = useEPIC;
        
        if(useTupExp)//pruned based on tuple-expansion matrix
            emat = searchSpace.tupExpEMat;
        else//use lower bounds
            emat = searchSpace.emat;
        
        if(useTupExp && useEPIC)//Doesn't make sense to use EPIC and tup-exp together, since
            //EPIC is meant to be added to pairwise lower-bound energy
            throw new RuntimeException("ERROR: Can't prune with both EPIC and tup-exp at the same time");
        
        tupEnum = new TupleEnumerator(searchSpace.pruneMat, emat, searchSpace.confSpace.numPos);
    }
    
    
    boolean prune(String methodName){
        //convenience method
        return prune(PruningMethod.getMethod(methodName));
    }
    
    
    boolean prune(PruningMethod method){
        //return whether we pruned something or not
        //A PruningMethod will just contain settings specifying the kind of Pruner
        //and return the right kinds of iterators and evaluate pruning conditions
        
        System.out.println("Starting pruning with " + method.name());
        
        PruningMatrix competitorPruneMat = searchSpace.competitorPruneMat;
        if(competitorPruneMat == null)
            competitorPruneMat = searchSpace.pruneMat;
        
        
        boolean prunedSomething = false;
        boolean prunedSomethingThisCycle;

        do {
            prunedSomethingThisCycle = false;
            
            ArrayList<RCTuple> candidates = enumerateCandidates(method);

            for( RCTuple cand : candidates ){
                
                double contELB = 0;
                if(useEPIC && cand.pos.size()>1)//EPIC gives us nothing for 1-pos pruning
                    contELB = searchSpace.epicMat.minContE(cand);
                
                if( ! searchSpace.pruneMat.isPruned(cand) ){
                    
                    boolean prunedCandidate = false;
                    
                    if(method.useCompetitor){
                        //any of the candidates may also be used as a competitor...
                        //for(RCTuple competitor : searchSpace.pruneMat.unprunedRCTuplesAtPos(cand.pos)){
                        //try having more competitors eliminated for speed
                        for(RCTuple competitor : competitorPruneMat.unprunedRCTuplesAtPos(cand.pos)){
                            
                            if(cand.isSameTuple(competitor) && contELB==0)//you can't prune yourself
                                //except if using EPIC (where we would be checking if contELB >= pruningInterval)
                                continue;
                            
                            if(typeDep){
                                //can only prune using competitors of same res type
                                if( !resTypesMatch(cand,competitor) )
                                    continue;
                            }
                                                        
                            if( canPrune(cand,competitor,method.cst,contELB) ){
                                prunedCandidate = true;
                                break;
                            }
                        }
                    }
                    else {
                        throw new RuntimeException("ERROR: Bounds pruning not supported yet");
                        //prunedCandidate = canPrune(cand,method.cst,contELB);//non-competitive pruning attempt
                    }
                    
                    if(prunedCandidate){
                        searchSpace.pruneMat.markAsPruned(cand);
                        prunedSomething = true;
                        prunedSomethingThisCycle = true;
                    }
                }
            }
            
        } while(prunedSomethingThisCycle);
        
        return prunedSomething;
    }
    
    
    public ArrayList<RCTuple> enumerateCandidates(PruningMethod method){
        //enumerate RC tuples that are candidates for pruning by the specified method
        if(method.numPos<=2)//we can afford to do all pairs
            return tupEnum.enumerateUnprunedTuples(method.numPos);
        else if(method.numPos==3){//let's do sparse triples
            ArrayList<ArrayList<Integer>> posTriples = tupEnum.topPositionTriples(triplesNumPartners);
            return tupEnum.enumerateUnprunedTuples(posTriples);
        }
        else//we could in principle enumerate everything for >3 positions, but it would likely take forever...
            throw new RuntimeException("ERROR: Number of positions not currently supported for pruning: "
                    +method.numPos);
    }
    
    
    boolean resTypesMatch(RCTuple tup1, RCTuple tup2){
        //Do the tuples have the same residue types?  If they do then we can
        //use one to prune the other by type-dependent DEE
        int numPosInTup = tup1.pos.size();
        
        for(int indexInTup=0; indexInTup<numPosInTup; indexInTup++){
            
            int pos1 = tup1.pos.get(indexInTup);
            int rc1 = tup1.RCs.get(indexInTup);
            String type1 = searchSpace.confSpace.posFlex.get(pos1).RCs.get(rc1).AAType;
            
            int pos2 = tup2.pos.get(indexInTup);
            int rc2 = tup2.RCs.get(indexInTup);
            String type2 = searchSpace.confSpace.posFlex.get(pos2).RCs.get(rc2).AAType;
            
            if(!type1.equalsIgnoreCase(type2))//this position doesn't match
                return false;
        }
        
        //if we get here they all match
        return true;
    }
    
    
    
    boolean canPrune(RCTuple cand, RCTuple comp, CheckSumType checkSumType, double contELB){
        //see if competitive pruning is valid for the given candidate and competitor
        //contELB: A lower bound on the continuous portion of the energy for conformations
        //containing cand
        
                
        //DEBUG!!!  Trying pruning of excessive local energy (for EPIC pruning)
        //contELB is all energy within cand, so very high contELB is physically unrealistic
        //(would probably cause unfolding/big BB change even if technically part of GMEC)
        double pairClashInterval = 10;//total energy allowed for pair before it's considered a clash
        double tripleClashInterval = 15;
        if(contELB>pruningInterval)//would check this if pruning cand w/ itself, but may not always do that
            return true;
        /*if(cand.pos.size() == 2 && contELB>pairClashInterval)
            return true;
        if(cand.pos.size() == 3 && contELB>tripleClashInterval)
            return true;*/
        
 
        
        
        double checkSum = emat.getInternalEnergy(cand);
        
        checkSum += contELB;
        checkSum -= emat.getInternalEnergy(comp);
        
        if(checkSumType == CheckSumType.GOLDSTEIN ){            
            
            for(int pos=0; pos<searchSpace.confSpace.numPos; pos++){
                if(!cand.pos.contains(pos)){
                    
                    double bestInteraction = Double.POSITIVE_INFINITY;
                    //if no rc's at pos are compatible with cand, we can set infinite checkSum --> prune cand
                    
                    for(int rc: searchSpace.pruneMat.unprunedRCsAtPos(pos)){
                        
                        if(searchSpace.pruneMat.isPruned(cand.addRC(pos,rc)))//(pos,rc) not compatible with cand
                            continue;
                        
                        double diffBound = minInteractionDiff(cand,comp,pos,rc);
                        
                        bestInteraction = Math.min(bestInteraction,
                               diffBound );
                    }
                    checkSum += bestInteraction;
                }
            }
        }
        else {
            throw new RuntimeException("ERROR: Not supporting indirect and conf-splitting pruning yet...");
        }
               
        return (checkSum>pruningInterval);
    }
    
    
    //For the rigid pruning with a pairwise matrix that is currently implemented,
    //min and maxInteraction are the same: just the sum of pairwise interactions
    //between (pos,rc) and all the RCs in RCTup
    double minInteractionDiff(RCTuple cand, RCTuple comp, int pos, int rc){
        double E = 0;
        
        ArrayList<HigherTupleFinder<Double>> candHigher = new ArrayList<>();
        ArrayList<HigherTupleFinder<Double>> compHigher = new ArrayList<>();
        
        for(int indexInTup=0; indexInTup<cand.pos.size(); indexInTup++){
            int pos2 = cand.pos.get(indexInTup);//should be same for comp
            int rc2 = cand.RCs.get(indexInTup);
            int rc2Comp = comp.RCs.get(indexInTup);
            
            double pairwiseE = emat.getPairwise(pos, rc, pos2, rc2)
                    - emat.getPairwise(pos, rc, pos2, rc2Comp);
            
            E += pairwiseE;
            
            HigherTupleFinder<Double> htfCand = emat.getHigherOrderTerms(pos, rc, pos2, rc2);
            if(htfCand!=null)
                candHigher.add(htfCand);
            HigherTupleFinder<Double> htfComp = emat.getHigherOrderTerms(pos, rc, pos2, rc2Comp);
            if(htfComp!=null)
                compHigher.add(htfComp);
        }
        
        if( ! (candHigher.isEmpty()&&compHigher.isEmpty()) )
            E += higherOrderContribGoldstein(cand,comp,pos,candHigher,compHigher);
        
        return E;
    }
    
    
    double higherOrderContribGoldstein(RCTuple cand, RCTuple comp, int pos, 
            ArrayList<HigherTupleFinder<Double>> candHigher, ArrayList<HigherTupleFinder<Double>> compHigher){
        //get higher-order terms for Goldstein pruning involving interactions of our candidate and competitor tuples
        //with position pos in some RC
        //the lists of higher-tuple finders are for that RC interacting with each residue in the candidate 
        //and competitor tuples
        
        
        //first get contribution within cand and comp
        //I'm only supporting triples for now...not sure how quads would be used
        //so hard to tell what would be an efficient strategy for them
        
        double contrib = higherOrderContribInternal(cand,candHigher) - higherOrderContribInternal(comp,compHigher);
        
        TreeSet<Integer> interactingPos = new TreeSet<>();//interacting positions outside tuple
        //using only those lower-numbered than pos, to avoid double-counting
        
        for(HigherTupleFinder<Double> htf : candHigher){
            for(int iPos : htf.getInteractingPos()){
                if( (!cand.pos.contains(iPos)) && iPos<pos ){
                    interactingPos.add(iPos);
                }
            }
        }
        for(HigherTupleFinder<Double> htf : compHigher){
            for(int iPos : htf.getInteractingPos()){
                if( (!cand.pos.contains(iPos)) && iPos<pos ){
                    interactingPos.add(iPos);
                }
            }
        }
        
        
        //ok now go through their interactions
        for(int iPos : interactingPos ){
                
                double levelBestE = Double.POSITIVE_INFINITY;//best value of contribution
                //from tup-iPos interaction
                ArrayList<Integer> allowedRCs = searchSpace.pruneMat.unprunedRCsAtPos(iPos);
                
                for( int rc : allowedRCs ){
                    
                    double interactionE = 0;
                    
                    //add up triple interactions of pos and ipos with any residues in cand
                    //subtract off interactions with residues in comp
                    for(HigherTupleFinder<Double> htf : candHigher){
                        interactionE += htf.getInteraction(iPos, rc);
                        
                        if( htf.getHigherInteractions(iPos, rc) != null )
                            throw new UnsupportedOperationException("ERROR: Not supporting energy >triples in DEE");
                    }
                    for(HigherTupleFinder<Double> htf : compHigher){
                        interactionE -= htf.getInteraction(iPos, rc);
                        
                        if( htf.getHigherInteractions(iPos, rc) != null )
                            throw new UnsupportedOperationException("ERROR: Not supporting energy >triples in DEE");
                    }
                    
                    //besides that only residues in definedTuple or levels below level2
                    levelBestE = Math.min(levelBestE,interactionE);
                }

                contrib += levelBestE;//add up contributions from different interacting positions iPos
        }
        
        return contrib;
    }
    
    
    
    double higherOrderContribInternal(RCTuple tup, ArrayList<HigherTupleFinder<Double>> htfList){
        //add up higher-order interactions in the given HigherTupleFinders with RCs in tup
        //HigherTupleFinders are themselves generated from pair interactions of tup with some other res
        //so divide by 2 to avoid double-counting
        double E = 0;
        
        for(HigherTupleFinder<Double> htf : htfList){
            for(int posCount=0; posCount<tup.pos.size(); posCount++){
                E += htf.getInteraction(tup.pos.get(posCount), tup.RCs.get(posCount));
                //will be 0 if current pos not in htf's interacting pos
                //(including if current pos is part of the pair that htf corresponds to)
            }
        }
        
        return E / 2;
    }
    
    
    
    
    boolean canPrune(RCTuple cand, CheckSumType checkSumType){
        //try to prune cand non-competitively
                
        double checkSum = emat.getInternalEnergy(cand);
                
        if(checkSumType == CheckSumType.BOUNDS ){
            
            //this is like the lower bound in the A* ConfTree
            //break up the full energy into contributions associated with different res
            for(int level=0; level<searchSpace.confSpace.numPos; level++){
                double resContribLB = Double.POSITIVE_INFINITY;//lower bound on contribution of this residue
                //resContribLB will be the minimum_{rc} of the lower bound assuming rc assigned to this level
                if(!cand.pos.contains(level)){//level not fully defined
                    for ( int rc : searchSpace.pruneMat.unprunedRCsAtPos(level) ) {//cache this?
                        resContribLB = Math.min( resContribLB, RCContributionLB(level,rc,cand) );
                    }
                }
                
                checkSum += resContribLB;
            }
            
        }
        else {
            throw new RuntimeException("ERROR: Unrecognized checksum type for non-competitive pruning: "+checkSumType.name());
        }
        
        return ( checkSum > boundsThreshold+pruningInterval );
    }
    
    
    
    //For Bounds pruning.  Based on ConfTree.
    double RCContributionLB(int level, int rc, RCTuple definedTuple){
        //Provide a lower bound on what the given rc at the given level can contribute to the energy
        //assume definedTuple
        
        double rcContrib = 0;
        
        //for this kind of lower bound, we need to split up the energy into the defined-tuple energy
        //plus "contributions" for each undefined residue
        //so we'll say the "contribution" consists of any interactions that include that residue
        //but do not include higher-numbered undefined residues
        for(int level2=0; level2<level; level2++){
            
            if(definedTuple.pos.contains(level2) || level2<level){//lower-numbered or defined residues
                
                double levelBestE = Double.POSITIVE_INFINITY;//best pairwise energy
                
                ArrayList<Integer> allowedRCs = null;
                if(definedTuple.pos.contains(level2)){
                    int index = definedTuple.pos.indexOf(level2);
                    int definedRC = definedTuple.RCs.get(index);
                    allowedRCs = new ArrayList<>();
                    allowedRCs.add(definedRC);
                }
                else
                    allowedRCs = searchSpace.pruneMat.unprunedRCsAtPos(level2);
                
                for( int rc2 : allowedRCs ){
                    
                    double interactionE = emat.getPairwise(level,rc,level2,rc2);
                    
                    //DEBUG!!!!
                    //double higherLB = higherOrderContribLB(partialConf,level,rc,level2,rc2,);
                    //add higher-order terms that involve rc, rc2, and
                    
                    //interactionE += higherLB;
                    
                    //besides that only residues in definedTuple or levels below level2
                    levelBestE = Math.min(levelBestE,interactionE);
                }

                rcContrib += levelBestE;
            }
        }
        
        return rcContrib;
    }
    
    
    
    public void pruneSteric(double stericThresh){
        //Prune 1- and 2-body terms exceeding steric thresh
        System.out.println("Starting steric pruning.");
        
        for(int numBodies=1; numBodies<=2; numBodies++){
            int numPruned = 0;
            
            ArrayList<RCTuple> candList = tupEnum.enumerateUnprunedTuples(numBodies);
            for(RCTuple cand : candList){
                
                double E;
                if(numBodies==1)
                    E = emat.getOneBody(cand.pos.get(0), cand.RCs.get(0));
                else
                    E = emat.getPairwise(cand.pos.get(0), cand.RCs.get(0), cand.pos.get(1), cand.RCs.get(1));
                
                if( E > stericThresh ){
                    searchSpace.pruneMat.markAsPruned(cand);
                    numPruned++;
                }
            }
            
            System.out.println("Pruned "+numPruned+" in "+numBodies+"-body steric pruning");
        }
    }

    
    
    /*PRUNING TODO:
     * 3. Goldstein higher-order awareness (so can do after tup-exp)
     * 4. Bounds (w/ higher-order)
     */
    
    
        
}