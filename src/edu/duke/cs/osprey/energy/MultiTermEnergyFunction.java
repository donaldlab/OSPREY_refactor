/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.energy;

import java.util.ArrayList;
import java.util.List;

import edu.duke.cs.osprey.dof.DegreeOfFreedom;
import edu.duke.cs.osprey.energy.forcefield.ResPairEnergy;
import edu.duke.cs.osprey.energy.forcefield.SingleResEnergy;
import edu.duke.cs.osprey.structure.Molecule;
import edu.duke.cs.osprey.structure.Residue;

/**
 *
 * @author mhall44
 */
//This an energy function consisting of terms that are other energy functions
//For example, it can be AMBER energy + dihedral energy
//or ( energy of a pair of residues ) - (energy of residue 1) - (energy of residue 2)
//Total energy = sum_i (coeff[i] * energy i)

public class MultiTermEnergyFunction implements EnergyFunction.DecomposableByDof {

	private static final long serialVersionUID = -3516267414126293331L;

	private static int NUM_THREADS = 1;
	private static boolean IS_VERBOSE = true;

	ArrayList<EnergyFunction> terms = new ArrayList<>();
	ArrayList<Double> coeffs = new ArrayList<>();
	ArrayList<Double> partialE = new ArrayList<>();
	ArrayList<Integer> indexes = new ArrayList<>();
	double preCompE = 0.0;

	//Constructor with no terms
	public MultiTermEnergyFunction(){

	}

	public void addTerm(EnergyFunction ef){
		terms.add(ef);
		coeffs.add(1.);
		partialE.add(0.0);
		indexes.add(indexes.size());
	}

	//add a term to this with a given coefficient
	public void addTermWithCoeff(EnergyFunction ef, double coeff){
		terms.add(ef);
		coeffs.add(coeff);
		partialE.add(0.0);
		indexes.add(indexes.size());
	}

	
	public static void setVerbose(boolean val) {
		IS_VERBOSE = val;
	}
	

	public static void setNumThreads( int threads ) { 
		NUM_THREADS = threads;

		if(NUM_THREADS < 1) NUM_THREADS = 1;

		else if(NUM_THREADS > Runtime.getRuntime().availableProcessors()) 
			NUM_THREADS = Runtime.getRuntime().availableProcessors();
		
		if (NUM_THREADS > 1) {
			// TODO: make user-friendly error message
			if(IS_VERBOSE) {
				System.out.println("\n\nWARNING (for Osprey programmers): energy function-level parallelism probably isn't the fastest tool anymore."
						+ " Try the new parallel SimpleEnergyMatrixCalculator and parallel/gpu-friendly ConfMinimizer classes instead.\n");
			}
		}
	}


	public static int getNumThreads() {
		return NUM_THREADS;
	}


	@Override
	public double getEnergy(){

		double E = 0;

		if(terms.size()!=coeffs.size()){
			throw new RuntimeException("ERROR: MultiTermEnergyFunction has "+terms.size()
			+" terms but "+coeffs.size()+" coefficients");
		}

		if(NUM_THREADS == 1) {
			for(int termNum=0; termNum<terms.size(); termNum++){
				double termE = terms.get(termNum).getEnergy();
				E += coeffs.get(termNum)*termE;
			}
		} else {

			indexes.parallelStream().forEach((term) -> partialE.set(term, terms.get(term).getEnergy()*coeffs.get(term)));
			for (int term = 0; term < indexes.size(); ++term) {
				E += partialE.get(term);
			}
		}

		if(Double.isNaN(E) || Double.isInfinite(E))//This can happen if there are positive and negative terms
			//with infinite energy...we assume this to be an impossible conformation
			//and thus return inifinity
			return Double.POSITIVE_INFINITY;

		return preCompE = E;
	}

	public ArrayList<EnergyFunction> getTerms() {
		return terms;
	}

	public ArrayList<Double> getCoeffs() {
		return coeffs;
	}

	@Override
	public List<EnergyFunction> decomposeByDof(Molecule m, List<DegreeOfFreedom> dofs) {
		List<EnergyFunction> dofEfuncs = new ArrayList<>();
		for (DegreeOfFreedom dof : dofs) {

			if(dof.getResidue() == null){//degree of freedom moves multiple residues
				dofEfuncs.add(this);//use the full energy
			}
			else {//degree of freedom moves just one residue
				dofEfuncs.add(makeResidueEfunc(dof.getResidue()));
			}
		}
		return dofEfuncs;
	}

	private EnergyFunction makeResidueEfunc(Residue residue) {

		// find all the terms that involve this residue
		MultiTermEnergyFunction resEfunc = new MultiTermEnergyFunction();
		for (EnergyFunction term : terms) {

			// TODO: could make the energy term worry about residue association queries
			// instead of switching on type here
			if (term instanceof SingleResEnergy) {
				SingleResEnergy singleResTerm = (SingleResEnergy)term;
				if (singleResTerm.getRes() == residue) {
					resEfunc.addTerm(singleResTerm);
				}
			} else if (term instanceof ResPairEnergy) {
				ResPairEnergy resPairTerm = (ResPairEnergy)term;
				if (resPairTerm.getRes1() == residue || resPairTerm.getRes2() == residue) {
					resEfunc.addTerm(resPairTerm);
				}
			} else {//not a one-body or pairwise forcefield term--may involve all residues
				resEfunc.addTerm(term);
				//throw new Error("Unsupported energy function term: " + term.getClass().getName());
			}
		}
		return resEfunc;
	}

	public double getPreCompE() {
		return preCompE;
	}

	public void setPreCompE(double in) {
		preCompE = in;
	}
}

