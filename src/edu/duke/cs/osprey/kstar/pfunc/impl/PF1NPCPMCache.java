package edu.duke.cs.osprey.kstar.pfunc.impl;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import edu.duke.cs.osprey.confspace.SearchProblem;
import edu.duke.cs.osprey.control.ConfigFileParser;
import edu.duke.cs.osprey.kstar.KSConf;
import edu.duke.cs.osprey.kstar.KSConfQ;
import edu.duke.cs.osprey.kstar.pfunc.PFAbstract;
import edu.duke.cs.osprey.tools.ObjectIO;

/**
 * 
 * @author Adegoke Ojewole (ao68@duke.edu)
 *
 */
@SuppressWarnings("serial")
public class PF1NPCPMCache extends PF1NPMCache implements Serializable {

	private ArrayList<Integer> indexes = new ArrayList<>();
	private ArrayList<SearchProblem> sps = new ArrayList<>();
	private ArrayList<KSConf> partialQConfs = new ArrayList<>();

	public PF1NPCPMCache( ArrayList<String> sequence, String checkPointPath, 
			ConfigFileParser cfp, SearchProblem sp, double EW_I0 ) {

		super( sequence, checkPointPath, cfp, sp, EW_I0 );
	}


	public void cleanup() {
		super.cleanup();
		sps.clear();
	}


	public void start() {

		try {

			setRunState(RunState.STARTED);
			
			// initialize parallel data structures
			indexes.clear();
			for( int it = 0; it < PFAbstract.getNumThreads(); ++it ) indexes.add(it);
			indexes.trimToSize();
			
			sps.clear();
			for( int i = 0; i < indexes.size(); ++i ) sps.add(null);
			sps.trimToSize();
			// create sps in parallel
			indexes.parallelStream().forEach(i -> {
				sps.set(i, (SearchProblem)ObjectIO.deepCopy(sp));
			});

			partialQConfs.clear();
			for( int it = 0; it < indexes.size(); ++it ) partialQConfs.add(null);
			partialQConfs.trimToSize();

			confs = new KSConfQ( this, sp, indexes.size() );

			// set pstar
			setPStar( confs.getNextConfELB() );

			confs.start();

			if( waitUntilCapacity )
				confs.waitUntilCapacity();

			startTime = System.currentTimeMillis();

		} catch(Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}

	}


	protected void iterate() throws Exception {
		
		synchronized( confs.qLock ) {

			int request = partialQConfs.size();
			int granted = 0;

			if( (granted = canSatisfy(request)) == 0 )
				return;

			// reduce the size of partialQconfs and indexes to match request
			while( partialQConfs.size() > granted ) {

				partialQConfs.remove(partialQConfs.size()-1);

				indexes.remove(indexes.size()-1);
			}

			for( int i = 0; i < Math.min(granted, partialQConfs.size()); ++i ) {
				partialQConfs.set(i, confs.deQueue());
			}

			minimizingConfs = minimizingConfs.add( BigInteger.valueOf(partialQConfs.size()) );

			if( confs.getState() == Thread.State.WAITING ) confs.qLock.notify();
		}

		// minimization hapens here
		accumulate(partialQConfs, false); 

		if( eAppx != EApproxReached.FALSE ) {
			// we leave this function
			confs.cleanUp(true);
		}	
	}


	protected void accumulate( ArrayList<KSConf> partialQConfs, boolean isMinimized ) throws Exception {

		if( !isMinimized ) {
			// we do not have a lock when minimizing
			indexes.parallelStream().forEach( i -> {
				partialQConfs.get(i).setMinEnergy( sps.get(i).minimizedEnergy(partialQConfs.get(i).getConfArray()) );
			});
		}

		double E = 0;

		// we need a current snapshot of qDagger, so we lock here
		synchronized( confs.qLock ) {
			// update q*, qDagger, minimizingConfs, and q' atomically
			Et = confs.size() > 0 ? confs.peekTail().getMinEnergyLB() 
					: partialQConfs.get(partialQConfs.size()-1).getMinEnergyLB();

			for( KSConf conf : partialQConfs ) {

				minimizingConfs = minimizingConfs.subtract( BigInteger.ONE );

				E = conf.getMinEnergy();
				updateQStar( conf );

				confs.setQDagger( confs.getQDagger().subtract( getBoltzmannWeight(conf.getMinEnergyLB()) ) );
				
				updateQPrime();

				// negative values of effective epsilon are disallowed
				if( (effectiveEpsilon = computeEffectiveEpsilon()) < 0 ) {
					eAppx = EApproxReached.NOT_POSSIBLE;
					return;
				}

				if( effectiveEpsilon <= targetEpsilon || maxKSConfsReached() ) break;
			}

			long currentTime = System.currentTimeMillis();

			if( !printedHeader ) printHeader();

			System.out.println(E + "\t" + effectiveEpsilon + "\t" + 
					getNumMinimized() + "\t" + getNumUnEnumerated() + "\t" + confs.size() + "\t" + ((currentTime-startTime)/1000));

			eAppx = effectiveEpsilon <= targetEpsilon || maxKSConfsReached() ? EApproxReached.TRUE: EApproxReached.FALSE;
		}
	}

}
