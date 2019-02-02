package edu.duke.cs.osprey.sofea;

import static edu.duke.cs.osprey.TestBase.*;
import static edu.duke.cs.osprey.tools.Log.log;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import edu.duke.cs.osprey.TestBase.TempFile;
import edu.duke.cs.osprey.astar.conf.ConfAStarTree;
import edu.duke.cs.osprey.astar.conf.ConfIndex;
import edu.duke.cs.osprey.astar.conf.RCs;
import edu.duke.cs.osprey.confspace.*;
import edu.duke.cs.osprey.ematrix.EnergyMatrix;
import edu.duke.cs.osprey.ematrix.SimplerEnergyMatrixCalculator;
import edu.duke.cs.osprey.energy.ConfEnergyCalculator;
import edu.duke.cs.osprey.energy.EnergyCalculator;
import edu.duke.cs.osprey.energy.EnergyPartition;
import edu.duke.cs.osprey.energy.forcefield.ForcefieldParams;
import edu.duke.cs.osprey.kstar.pfunc.BoltzmannCalculator;
import edu.duke.cs.osprey.kstar.pfunc.GradientDescentPfunc;
import edu.duke.cs.osprey.kstar.pfunc.PartitionFunction;
import edu.duke.cs.osprey.parallelism.Parallelism;
import edu.duke.cs.osprey.structure.Molecule;
import edu.duke.cs.osprey.structure.PDBIO;
import edu.duke.cs.osprey.tools.Log;
import edu.duke.cs.osprey.tools.MathTools.BigIntegerBounds;
import edu.duke.cs.osprey.tools.MathTools.BigDecimalBounds;
import edu.duke.cs.osprey.tools.MathTools.DoubleBounds;
import edu.duke.cs.osprey.tools.Streams;
import org.junit.Test;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Consumer;


public class TestSofea {

	private static final Parallelism fullCPUParallelism = Parallelism.makeCpu(Parallelism.getMaxNumCPUs());
	private static final File tmpdir = new File(System.getProperty("java.io.tmpdir"), "testSofea");
	private static final MathContext mathContext = new MathContext(16, RoundingMode.HALF_UP); // TODO: how much precision do we really need here?
	private static final BoltzmannCalculator bcalc = new BoltzmannCalculator(mathContext);
	private static final double epsilonG = 1e-3;

	static {
		tmpdir.mkdirs();
	}


	@Test
	public void test_Binding1CC8Flex3_Traditional_EnergyBounds() {
		Design design = Designs.Binding1CC8Flex3_Traditional.get();
		assertEnergyBounds(design);
	}
	@Test
	public void test_Binding1CC8Flex3_Traditional_LeafCounts() {
		Design design = Designs.Binding1CC8Flex3_Traditional.get();
		assertLeafCounts(design);
	}
	@Test
	public void test_Binding1CC8Flex3_Traditional_ZPathBounds() {
		Design design = Designs.Binding1CC8Flex3_Traditional.get();
		assertZPathBounds(design);
	}
	@Test
	public void test_Binding1CC8Flex3_Traditional_ZSumBounds() {
		Design design = Designs.Binding1CC8Flex3_Traditional.get();
		assertZSumBounds(design);
	}
	@Test
	public void test_Binding1CC8Flex3_Traditional_CalcG() {
		Design design = Designs.Binding1CC8Flex3_Traditional.get();
		assertGStates(design, Collections.emptyList(), -64.654, -31.490, -23.015);
	}


	@Test
	public void test_Stability1CC8Mut3_Traditional_LeafCounts() {
		Design design = Designs.Stability1CC8Mut3_Traditional.get();
		assertLeafCounts(design);
	}
	@Test
	public void test_Stability1CC8Mut3_Traditional_CalcG() {
		Design design = Designs.Stability1CC8Mut3_Traditional.get();
		assertGStates(design, Arrays.asList("LYS", "GLN", "LEU"), -43.255);
		assertGStates(design, Arrays.asList("VAL", "GLN", "LEU"), -16.417);
		assertGStates(design, Arrays.asList("LEU", "GLN", "LEU"), -19.701);
		assertGStates(design, Arrays.asList("LYS", "VAL", "LEU"), -32.173);
		assertGStates(design, Arrays.asList("LYS", "LEU", "LEU"), -33.006);
		assertGStates(design, Arrays.asList("VAL", "VAL", "LEU"), -5.605);
		assertGStates(design, Arrays.asList("VAL", "LEU", "LEU"), -6.246);
		assertGStates(design, Arrays.asList("LEU", "VAL", "LEU"), -9.091);
		assertGStates(design, Arrays.asList("LEU", "LEU", "LEU"), -9.927);
		assertGStates(design, Arrays.asList("LYS", "GLN", "VAL"), -33.344);
		assertGStates(design, Arrays.asList("VAL", "GLN", "VAL"), -7.424);
		assertGStates(design, Arrays.asList("LEU", "GLN", "VAL"), -18.591);
		assertGStates(design, Arrays.asList("LYS", "VAL", "VAL"), -22.660);
		assertGStates(design, Arrays.asList("LYS", "LEU", "VAL"), -23.530);
		assertGStates(design, Arrays.asList("VAL", "VAL", "VAL"), 2.988);
		assertGStates(design, Arrays.asList("VAL", "LEU", "VAL"), 2.311);
		assertGStates(design, Arrays.asList("LEU", "VAL", "VAL"), -7.970);
		assertGStates(design, Arrays.asList("LEU", "LEU", "VAL"), -8.818);
	}
	private static void assertResults_Stability1CC8Mut3_Traditional(Results results) {
		results.assertGSequenced(Arrays.asList("LYS", "GLN", "LEU"), -43.255);
		results.assertGSequenced(Arrays.asList("VAL", "GLN", "LEU"), -16.417);
		results.assertGSequenced(Arrays.asList("LEU", "GLN", "LEU"), -19.701);
		results.assertGSequenced(Arrays.asList("LYS", "VAL", "LEU"), -32.173);
		results.assertGSequenced(Arrays.asList("LYS", "LEU", "LEU"), -33.006);
		results.assertGSequenced(Arrays.asList("VAL", "VAL", "LEU"), -5.605);
		results.assertGSequenced(Arrays.asList("VAL", "LEU", "LEU"), -6.246);
		results.assertGSequenced(Arrays.asList("LEU", "VAL", "LEU"), -9.091);
		results.assertGSequenced(Arrays.asList("LEU", "LEU", "LEU"), -9.927);
		results.assertGSequenced(Arrays.asList("LYS", "GLN", "VAL"), -33.344);
		results.assertGSequenced(Arrays.asList("VAL", "GLN", "VAL"), -7.424);
		results.assertGSequenced(Arrays.asList("LEU", "GLN", "VAL"), -18.591);
		results.assertGSequenced(Arrays.asList("LYS", "VAL", "VAL"), -22.660);
		results.assertGSequenced(Arrays.asList("LYS", "LEU", "VAL"), -23.530);
		results.assertGSequenced(Arrays.asList("VAL", "VAL", "VAL"), 2.988);
		results.assertGSequenced(Arrays.asList("VAL", "LEU", "VAL"), 2.311);
		results.assertGSequenced(Arrays.asList("LEU", "VAL", "VAL"), -7.970);
		results.assertGSequenced(Arrays.asList("LEU", "LEU", "VAL"), -8.818);
	}
	@Test
	public void test_Stability1CC8Mut3_Traditional_SingleStep() {
		stepUntilExhaustion(
			Designs.Stability1CC8Mut3_Traditional.get(),
			Double.POSITIVE_INFINITY,
			1024*1024,
			TestSofea::assertResults_Stability1CC8Mut3_Traditional
		);
	}
	@Test
	public void test_Stability1CC8Mut3_Traditional_MultiStepHiMem() {
		stepUntilExhaustion(
			Designs.Stability1CC8Mut3_Traditional.get(),
			50.0,
			1024*1024,
			TestSofea::assertResults_Stability1CC8Mut3_Traditional
		);
	}
	@Test
	public void test_Stability1CC8Mut3_Traditional_MultiStepLoMem() {
		stepUntilExhaustion(
			Designs.Stability1CC8Mut3_Traditional.get(),
			3.0,
			200,
			TestSofea::assertResults_Stability1CC8Mut3_Traditional
		);
	}


	@Test
	public void test_Binding1CC8Mut2Flex1_Traditional_LeafCounts() {
		Design design = Designs.Binding1CC8Mut2Flex1_Traditional.get();
		assertLeafCounts(design);
	}
	@Test
	public void test_Binding1CC8Mut2Flex1_Traditional_ZPathBounds() {
		Design design = Designs.Binding1CC8Mut2Flex1_Traditional.get();
		assertZPathBounds(design);
	}
	@Test
	public void test_Binding1CC8Mut2Flex1_Traditional_ZSumBounds() {
		Design design = Designs.Binding1CC8Mut2Flex1_Traditional.get();
		assertZSumBounds(design);
	}
	@Test
	public void test_Binding1CC8Mut2Flex1_Traditional_CalcG() {
		Design design = Designs.Binding1CC8Mut2Flex1_Traditional.get();
		assertGStates(design, Arrays.asList("GLN", "LEU"), -64.645, -31.187, -23.015);
		assertGStates(design, Arrays.asList("VAL", "LEU"), -50.170, -20.093, -23.015);
		assertGStates(design, Arrays.asList("LEU", "LEU"), -44.926, -20.929, -23.015);
		assertGStates(design, Arrays.asList("GLN", "VAL"), -54.359, -21.353, -23.015);
		assertGStates(design, Arrays.asList("VAL", "VAL"), -40.287, -10.657, -23.015);
		assertGStates(design, Arrays.asList("LEU", "VAL"), -35.082, -11.533, -23.015);
	}
	private static void assertResults_Binding1CC8Mut2Flex1_Traditional(Results results) {
		results.assertGUnsequenced(-23.015);
		results.assertGSequenced(Arrays.asList("GLN", "LEU"), -64.645, -31.187);
		results.assertGSequenced(Arrays.asList("VAL", "LEU"), -50.170, -20.093);
		results.assertGSequenced(Arrays.asList("LEU", "LEU"), -44.926, -20.929);
		results.assertGSequenced(Arrays.asList("GLN", "VAL"), -54.359, -21.353);
		results.assertGSequenced(Arrays.asList("VAL", "VAL"), -40.287, -10.657);
		results.assertGSequenced(Arrays.asList("LEU", "VAL"), -35.082, -11.533);
	}
	@Test
	public void test_Binding1CC8Mut2Flex1_Traditional_SingleStep() {
		stepUntilExhaustion(
			Designs.Binding1CC8Mut2Flex1_Traditional.get(),
			Double.POSITIVE_INFINITY,
			1024*1024,
			TestSofea::assertResults_Binding1CC8Mut2Flex1_Traditional
		);
	}
	@Test
	public void test_Binding1CC8Mut2Flex1_Traditional_MultiStepHiMem() {
		stepUntilExhaustion(
			Designs.Binding1CC8Mut2Flex1_Traditional.get(),
			50.0,
			1024*1024,
			TestSofea::assertResults_Binding1CC8Mut2Flex1_Traditional
		);
	}
	@Test
	public void test_Binding1CC8Mut2Flex1_Traditional_MultiStepLoMem() {
		stepUntilExhaustion(
			Designs.Binding1CC8Mut2Flex1_Traditional.get(),
			3.0,
			4*1024,
			TestSofea::assertResults_Binding1CC8Mut2Flex1_Traditional
		);
	}


	// too big for brute-force tests
	@Test
	public void test_Binding1CC8Mut2Flex3_Traditional_CalcG() {
		Design design = Designs.Binding1CC8Mut2Flex3_Traditional.get();
		assertGStates(design, Arrays.asList("GLN", "LEU"), -113.643, -31.187, -71.258);
		assertGStates(design, Arrays.asList("VAL", "LEU"), -99.541, -20.093, -71.258);
		assertGStates(design, Arrays.asList("LEU", "LEU"), -96.647, -20.929, -71.258);
		assertGStates(design, Arrays.asList("GLN", "VAL"), -103.336, -21.353, -71.258);
		assertGStates(design, Arrays.asList("VAL", "VAL"), -89.609, -10.657, -71.258);
		assertGStates(design, Arrays.asList("LEU", "VAL"), -86.786, -11.533, -71.258);
	}
	private static void assertResults_Binding1CC8Mut2Flex3_Traditional(Results results) {
		results.assertGUnsequenced(-71.258);
		results.assertGSequenced(Arrays.asList("GLN", "LEU"), -113.643, -31.187);
		results.assertGSequenced(Arrays.asList("VAL", "LEU"), -99.541, -20.093);
		results.assertGSequenced(Arrays.asList("LEU", "LEU"), -96.647, -20.929);
		results.assertGSequenced(Arrays.asList("GLN", "VAL"), -103.336, -21.353);
		results.assertGSequenced(Arrays.asList("VAL", "VAL"), -89.609, -10.657);
		results.assertGSequenced(Arrays.asList("LEU", "VAL"), -86.786, -11.533);
	}
	@Test
	public void test_Binding1CC8Mut2Flex3_Traditional_SingleStep() { // TODO: NEXTTIME: this worked once, but now it doesn't?
		stepUntilAllStatesPrecise(
			Designs.Binding1CC8Mut2Flex3_Traditional.get(),
			Double.POSITIVE_INFINITY,
			1024*1024,
			TestSofea::assertResults_Binding1CC8Mut2Flex3_Traditional
		);
	}
	@Test
	public void test_Binding1CC8Mut2Flex3_Traditional_SingleStep_2Threads() {
		stepUntilAllStatesPrecise(
			Designs.Binding1CC8Mut2Flex3_Traditional.get(),
			Double.POSITIVE_INFINITY,
			1024*1024,
			2,
			TestSofea::assertResults_Binding1CC8Mut2Flex3_Traditional
		);
	}
	@Test
	public void test_Binding1CC8Mut2Flex3_Traditional_SingleStep_4Threads() {
		stepUntilAllStatesPrecise(
			Designs.Binding1CC8Mut2Flex3_Traditional.get(),
			Double.POSITIVE_INFINITY,
			1024*1024,
			4,
			TestSofea::assertResults_Binding1CC8Mut2Flex3_Traditional
		);
	}
	@Test
	public void test_Binding1CC8Mut2Flex3_Traditional_MultiStepHiMem() {
		stepUntilAllStatesPrecise(
			Designs.Binding1CC8Mut2Flex3_Traditional.get(),
			50.0,
			1024*1024,
			TestSofea::assertResults_Binding1CC8Mut2Flex3_Traditional
		);
	}
	@Test
	public void test_Binding1CC8Mut2Flex3_Traditional_MultiStepHiMem_2Threads() {
		stepUntilAllStatesPrecise(
			Designs.Binding1CC8Mut2Flex3_Traditional.get(),
			50.0,
			1024*1024,
			2,
			TestSofea::assertResults_Binding1CC8Mut2Flex3_Traditional
		);
	}
	@Test
	public void test_Binding1CC8Mut2Flex3_Traditional_MultiStepHiMem_4Threads() {
		stepUntilAllStatesPrecise(
			Designs.Binding1CC8Mut2Flex3_Traditional.get(),
			50.0,
			1024*1024,
			4,
			TestSofea::assertResults_Binding1CC8Mut2Flex3_Traditional
		);
	}
	@Test
	public void test_Binding1CC8Mut2Flex3_Traditional_MultiStepLoMem() {
		stepUntilAllStatesPrecise(
			Designs.Binding1CC8Mut2Flex3_Traditional.get(),
			50.0,
			16*1024,
			TestSofea::assertResults_Binding1CC8Mut2Flex3_Traditional
		);
	}
	@Test
	public void test_Binding1CC8Mut2Flex3_Traditional_MultiStepLoMem_2Threads() {
		stepUntilAllStatesPrecise(
			Designs.Binding1CC8Mut2Flex3_Traditional.get(),
			50.0,
			16*1024,
			2,
			TestSofea::assertResults_Binding1CC8Mut2Flex3_Traditional
		);
	}
	@Test
	public void test_Binding1CC8Mut2Flex3_Traditional_MultiStepLoMem_4Threads() {
		stepUntilAllStatesPrecise(
			Designs.Binding1CC8Mut2Flex3_Traditional.get(),
			50.0,
			16*1024,
			4,
			TestSofea::assertResults_Binding1CC8Mut2Flex3_Traditional
		);
	}


	@Test
	public void test_Binding1CC8Mut2Flex1_AllOnPairs_LeafCounts() {
		Design design = Designs.Binding1CC8Mut2Flex1_AllOnPairs.get();
		assertLeafCounts(design);
	}
	@Test
	public void test_Binding1CC8Mut2Flex1_AllOnPairs_ZPathBounds() {
		Design design = Designs.Binding1CC8Mut2Flex1_AllOnPairs.get();
		assertZPathBounds(design);
	}
	@Test
	public void test_Binding1CC8Mut2Flex1_AllOnPairs_ZSumBounds() {
		Design design = Designs.Binding1CC8Mut2Flex1_AllOnPairs.get();
		assertZSumBounds(design);
	}
	@Test
	public void test_Binding1CC8Mut2Flex1_AllOnPairs_CalcG() {
		Design design = Designs.Binding1CC8Mut2Flex1_AllOnPairs.get();
		assertGStates(design, Arrays.asList("GLN", "LEU"), -64.645, -31.187, -23.015);
		assertGStates(design, Arrays.asList("VAL", "LEU"), -50.170, -20.093, -23.015);
		assertGStates(design, Arrays.asList("LEU", "LEU"), -44.926, -20.929, -23.015);
		assertGStates(design, Arrays.asList("GLN", "VAL"), -54.359, -21.353, -23.015);
		assertGStates(design, Arrays.asList("VAL", "VAL"), -40.287, -10.657, -23.015);
		assertGStates(design, Arrays.asList("LEU", "VAL"), -35.082, -11.533, -23.015);
	}
	private static void assertResults_Binding1CC8Mut2Flex1_AllOnPairs(Results results) {
		results.assertGUnsequenced(-23.015);
		results.assertGSequenced(Arrays.asList("GLN", "LEU"), -64.645, -31.187);
		results.assertGSequenced(Arrays.asList("VAL", "LEU"), -50.170, -20.093);
		results.assertGSequenced(Arrays.asList("LEU", "LEU"), -44.926, -20.929);
		results.assertGSequenced(Arrays.asList("GLN", "VAL"), -54.359, -21.353);
		results.assertGSequenced(Arrays.asList("VAL", "VAL"), -40.287, -10.657);
		results.assertGSequenced(Arrays.asList("LEU", "VAL"), -35.082, -11.533);
	}
	@Test
	public void test_Binding1CC8Mut2Flex1_AllOnPairs_SingleStep() {
		stepUntilExhaustion(
			Designs.Binding1CC8Mut2Flex1_AllOnPairs.get(),
			Double.POSITIVE_INFINITY,
			1024*1024,
			TestSofea::assertResults_Binding1CC8Mut2Flex1_AllOnPairs
		);
	}
	@Test
	public void test_Binding1CC8Mut2Flex1_AllOnPairs_MultiStepHiMem() {
		stepUntilExhaustion(
			Designs.Binding1CC8Mut2Flex1_AllOnPairs.get(),
			50.0,
			1024*1024,
			TestSofea::assertResults_Binding1CC8Mut2Flex1_AllOnPairs
		);
	}
	@Test
	public void test_Binding1CC8Mut2Flex1_AllOnPairs_MultiStepLoMem() {
		stepUntilExhaustion(
			Designs.Binding1CC8Mut2Flex1_AllOnPairs.get(),
			50.0,
			16*1024,
			TestSofea::assertResults_Binding1CC8Mut2Flex1_AllOnPairs
		);
	}


	/** brute forces every node in the tree and calls the supplied block with a ConfIndex instance describing the node */
	public static void forEachNode(Sofea.StateInfo stateInfo, Consumer<ConfIndex> block) {

		ConfIndex index = stateInfo.makeConfIndex();

		// NOTE: java has a hard time with recursive lambdas,
		// so use an array to work around the compiler's limitations
		Runnable[] f = { null };
		f[0] = () -> {

			// call the supplied block
			block.accept(index);

			// stop recursion if this is a leaf node
			if (index.isFullyDefined()) {
				return;
			}

			// otherwise, recurse
			int pos = index.numDefined;
			for (int rc : stateInfo.rcs.get(pos)) {
				index.assignInPlace(pos, rc);
				f[0].run();
				index.unassignInPlace(pos);
			}
		};
		f[0].run();
	}

	/** WARNING: brute force method, will compute minimized energies for every conf and partial conf, only use on small designs */
	public static void assertEnergyBounds(Design design) {
		try (Ecalcs ecalcs = design.makeEcalcs()) {

			Sofea sofea = new Sofea.Builder(design.confSpace)
				.configEachState(state -> design.configState(state, ecalcs))
				.setMathContext(mathContext)
				.build();

				for (MultiStateConfSpace.State state : design.confSpace.states) {
					Sofea.StateInfo stateInfo = sofea.getStateInfo(state);
					ConfEnergyCalculator confEcalc = ecalcs.getMinimizing(state);

					try (Sofea.StateInfo.Confs confs = stateInfo.new Confs()) {

						forEachNode(stateInfo, index -> {

							RCTuple tuple = new RCTuple(index);
							DoubleBounds energyBounds = new DoubleBounds(
								design.ematsLower[state.index].getInternalEnergy(tuple),
								design.ematsUpper[state.index].getInternalEnergy(tuple)
							);

							double energy = confEcalc.calcEnergy(tuple, confs.table);

							assertThat(energyBounds, isRelativeBound(energy, 1e-3));
						});
					}
				}
		}
	}


	public static void assertLeafCounts(Design design) {
		try (Ecalcs ecalcs = design.makeEcalcs()) {

			Sofea sofea = new Sofea.Builder(design.confSpace)
				.configEachState(state -> design.configState(state, ecalcs))
				.setMathContext(mathContext)
				.build();

			for (MultiStateConfSpace.State state : design.confSpace.states) {
				Sofea.StateInfo stateInfo = sofea.getStateInfo(state);

				forEachNode(stateInfo, index -> {
					BigIntegerBounds bounds = stateInfo.boundLeavesPerSequence(index);
					Map<Sequence,BigInteger> counts = stateInfo.countLeavesBySequence(index);
					BigInteger minCount = counts.values().stream().min(BigInteger::compareTo).orElse(null);
					BigInteger maxCount = counts.values().stream().max(BigInteger::compareTo).orElse(null);
					assertThat(bounds.lower, lessThanOrEqualTo(minCount));
					assertThat(bounds.upper, greaterThanOrEqualTo(maxCount));
				});
			}
		}
	}

	public static void assertZPathBounds(Design design) {
		try (Ecalcs ecalcs = design.makeEcalcs()) {

			Sofea sofea = new Sofea.Builder(design.confSpace)
				.configEachState(state -> design.configState(state, ecalcs))
				.setMathContext(mathContext)
				.build();

			for (MultiStateConfSpace.State state : design.confSpace.states) {
				Sofea.StateInfo stateInfo = sofea.getStateInfo(state);
				try (Sofea.StateInfo.Confs confs = stateInfo.new Confs()) {

					forEachNode(stateInfo, index -> {
						BigDecimalBounds exact = stateInfo.calcZPathBoundsExact(index, stateInfo.rcs, confs.table);
						BigDecimalBounds bounds = stateInfo.calcZPathBounds(index, stateInfo.rcs);
						assertThat(bounds, isRelativeBound(exact, 1e-3));
					});
				}
			}
		}
	}

	public static void assertZSumBounds(Design design) {
		try (Ecalcs ecalcs = design.makeEcalcs()) {

			Sofea sofea = new Sofea.Builder(design.confSpace)
				.configEachState(state -> design.configState(state, ecalcs))
				.setMathContext(mathContext)
				.build();

			for (MultiStateConfSpace.State state : design.confSpace.states) {
				Sofea.StateInfo stateInfo = sofea.getStateInfo(state);
				try (Sofea.StateInfo.Confs confs = stateInfo.new Confs()) {

					forEachNode(stateInfo, index -> {

						// skip leaf nodes
						if (index.isFullyDefined()) {
							return;
						}

						BigDecimalBounds bounds = stateInfo.calcZSumBounds(index, stateInfo.rcs);
						BigDecimal exact = stateInfo.calcZSum(index, stateInfo.rcs, confs.table);
						if (bounds != null) {
							assertThat(bounds, isAbsoluteBound(exact, 1e-3));
						} else {
							assertThat(exact.doubleValue(), isAbsolutely(0.0, 1e-3));
						}
					});
				}
			}
		}
	}

	public static void main(String[] args) {

		// calc exact state g values for all designs
		for (Designs designId : Designs.values()) {

			Design design = designId.get();
			log("%s", designId);

			for (Sequence seq : design.confSpace.seqSpace.getSequences()) {
				log("Arrays.asList(%s), %s",
					Streams.joinToString(seq.assignments(), ", ", a -> "\"" + a.getResType().name + "\""),
					Streams.joinToString(
						calcGStatesAStar(design, seq),
						", ",
						(double d) -> String.format("%.3f", d)
					)
				);
			}
		}
	}

	public static double[] calcGStatesAStar(Design design, Sequence seq) {

		try (Ecalcs ecalcs = design.makeEcalcs()) {

			Sofea sofea = new Sofea.Builder(design.confSpace)
				.configEachState(state -> design.configState(state, ecalcs))
				.setMathContext(mathContext)
				.build();

			return design.confSpace.states.stream()
				.mapToDouble(state -> {

					RCs rcs = seq.makeRCs(state.confSpace);
					ConfAStarTree astar = new ConfAStarTree.Builder(design.ematsLower[state.index], rcs)
						.setTraditional()
						.build();

					try (Sofea.StateInfo.Confs confs = sofea.getStateInfo(state).new Confs()) {

						// TODO: NEXTTIME: GradientDescentPfunc is returing some bad answers in multi-thread mode? What broke it?
						GradientDescentPfunc pfunc = new GradientDescentPfunc(ecalcs.getMinimizing(state));
						pfunc.setConfTable(confs.table);
						pfunc.init(astar, rcs.getNumConformations(), 0.00001);
						pfunc.setStabilityThreshold(null); // turn the damn thing off!
						pfunc.compute();
						PartitionFunction.Result result = pfunc.makeResult();

						DoubleBounds g = new DoubleBounds(
							bcalc.freeEnergyPrecise(result.values.calcUpperBound()),
							bcalc.freeEnergyPrecise(result.values.calcLowerBound())
						);
						if (g.size() >= epsilonG) {
							throw new Error(String.format("need smaller epsilon: %s   pfunc=[%12e,%12e]=[%s,%s] d=%.8f  %s",
								g.toString(4, 9),
								result.values.calcLowerBound().doubleValue(),
								result.values.calcUpperBound().doubleValue(),
								Log.formatBigLn(result.values.calcLowerBound()),
								Log.formatBigLn(result.values.calcUpperBound()),
								result.values.getEffectiveEpsilon(),
								result.status
							));
						}
						return g.lower;

						/* brute force A*, too slow for all but tiny designs
						BigMath z = new BigMath(bcalc.mathContext).set(0.0);
						ConfEnergyCalculator confEcalc = ecalcs.getMinimizing(state);
						while (true) {

							ConfSearch.ScoredConf conf = astar.nextConf();
							if (conf == null) {
								break;
							}

							confEcalc.calcEnergyAsync(conf, confs.table, econf -> {
								z.add(bcalc.calcPrecise(econf.getEnergy()));
							});
						}
						confEcalc.ecalc.tasks.waitForFinish();

						return bcalc.freeEnergyPrecise(z.get());
						*/
					}
				})
				.toArray();
		}
	}

	public void assertGStates(Design design, List<String> resTypes, double ... expectedG) {
		assertGStates(
			design,
			design.confSpace.seqSpace.makeSequence(resTypes),
			expectedG
		);
	}

	public void assertGStates(Design design, Sequence seq, double ... expectedG) {
		try (Ecalcs ecalcs = design.makeEcalcs()) {

			Sofea sofea = new Sofea.Builder(design.confSpace)
				.configEachState(state -> design.configState(state, ecalcs))
				.setMathContext(mathContext)
				.build();

			double[] g = design.confSpace.states.stream()
				.mapToDouble(state -> bcalc.freeEnergyPrecise(sofea.calcZSum(seq, state)))
				.toArray();

			assertThat(g, isAbsolutely(expectedG, epsilonG));
		}
	}

	private static class Results {

		public final Design design;
		public final Map<Sequence,SeqDB.SeqInfo> sequenced = new HashMap<>();
		public final List<BigDecimalBounds> unsequenced = new ArrayList<>();

		public Results(Design design, SeqDB seqdb) {

			this.design = design;

			for (Sequence seq : design.confSpace.seqSpace.getSequences()) {
				sequenced.put(seq, seqdb.getSequencedZSumBounds(seq));
			}
			for (MultiStateConfSpace.State state : design.confSpace.unsequencedStates) {
				unsequenced.add(seqdb.getUnsequencedZSumBounds(state));
			}
		}

		public void assertGUnsequenced(double ... expectedG) {
			assertThat(design.confSpace.unsequencedStates.size(), is(expectedG.length));
			for (MultiStateConfSpace.State state : design.confSpace.unsequencedStates) {
				double expG = expectedG[state.unsequencedIndex];
				DoubleBounds obsG = bcalc.freeEnergyPrecise(unsequenced.get(state.unsequencedIndex));
				assertThat(obsG, isAbsoluteBound(expG, epsilonG));
			}
		}

		public void assertGSequenced(List<String> resTypes, double ... expectedG) {
			assertThat(design.confSpace.sequencedStates.size(), is(expectedG.length));
			Sequence seq = design.confSpace.seqSpace.makeSequence(resTypes);
			SeqDB.SeqInfo seqInfo = sequenced.get(seq);
			for (MultiStateConfSpace.State state : design.confSpace.sequencedStates) {
				double expG = expectedG[state.sequencedIndex];
				DoubleBounds obsG = bcalc.freeEnergyPrecise(seqInfo.get(state));
				assertThat(obsG, isAbsoluteBound(expG, epsilonG));
			}
		}
	}

	private interface IntermediateChecker {
		void check(Results results);
	}

	public void stepUntilExhaustion(Design design, double sweepDivisor, long fringeDBBytes, IntermediateChecker checker) {
		stepUntilExhaustion(design, sweepDivisor, fringeDBBytes, 1, checker);
	}

	public void stepUntilExhaustion(Design design, double sweepDivisor, long fringeDBBytes, int numThreads, IntermediateChecker checker) {
		try (TempFile fringedbFile = new TempFile(tmpdir, "fringe.db")) {
		try (TempFile seqdbFile = new TempFile(tmpdir, "seq.db")) {
		try (Ecalcs ecalcs = design.makeEcalcs()) {

			Sofea sofea = new Sofea.Builder(design.confSpace)
				.setFringeDBFile(fringedbFile)
				.setFringeDBBytes(fringeDBBytes)
				.setSeqDBFile(seqdbFile)
				.setSweepDivisor(sweepDivisor)
				.configEachState(state -> design.configState(state, ecalcs))
				.setParallelism(Parallelism.makeCpu(numThreads))
				.setMathContext(mathContext)
				.build();

			sofea.init(true);

			// refine, and check results between each sweep
			sofea.refine((seqdb, fringedb, sweepCount, bcalc) -> {
				checker.check(new Results(design, seqdb));

				// keep iterating until exhaustion
				return Sofea.Criterion.Satisfied.KeepIterating;
			});

			// check results once more at end, just for good measure
			try (SeqDB seqdb = sofea.openSeqDB()) {
				checker.check(new Results(design, seqdb));
			}
		}}}
	}

	public void stepUntilAllStatesPrecise(Design design, double sweepDivisor, long fringeDBBytes, IntermediateChecker checker) {
		stepUntilAllStatesPrecise(design, sweepDivisor, fringeDBBytes, 1, checker);
	}

	public void stepUntilAllStatesPrecise(Design design, double sweepDivisor, long fringeDBBytes, int numThreads, IntermediateChecker checker) {
		try (TempFile fringedbFile = new TempFile(tmpdir, "fringe.db")) {
		try (TempFile seqdbFile = new TempFile(tmpdir, "seq.db")) {
		try (Ecalcs ecalcs = design.makeEcalcs()) {

			Sofea sofea = new Sofea.Builder(design.confSpace)
				.setFringeDBFile(fringedbFile)
				.setFringeDBBytes(fringeDBBytes)
				.setSeqDBFile(seqdbFile)
				.setSweepDivisor(sweepDivisor)
				.configEachState(state -> design.configState(state, ecalcs))
				.setParallelism(Parallelism.makeCpu(numThreads))
				.build();

			sofea.init(true);

			// refine, and check results between each sweep
			sofea.refine((seqdb, fringedb, sweepCount, bcalc) -> {
				checker.check(new Results(design, seqdb));

				// are G estimates for all states precise enough?
				for (Sequence seq : design.confSpace.seqSpace.getSequences()) {
					SeqDB.SeqInfo seqInfo = seqdb.getSequencedZSumBounds(seq);
					for (MultiStateConfSpace.State state : design.confSpace.states) {
						BigDecimalBounds z;
						if (state.isSequenced) {
							z = seqInfo.get(state);
						} else {
							z = seqdb.getUnsequencedZSumBounds(state);
						}
						DoubleBounds g = bcalc.freeEnergyPrecise(z);
						if (g.size() > epsilonG) {

							// nope, keep iterating
							return Sofea.Criterion.Satisfied.KeepIterating;
						}
					}
				}
				return Sofea.Criterion.Satisfied.KeepIterating;
			});

			// check results once more at end, just for good measure
			try (SeqDB seqdb = sofea.openSeqDB()) {
				checker.check(new Results(design, seqdb));
			}
		}}}
	}

	private static enum ConfSpaces {

		// WARNING: if you change any details here, make sure to delete any cached files in `TestSofea.tmpdir`

		Binding1CC8Flex3 {

			@Override
			public MultiStateConfSpace make() {

				Molecule pdb = PDBIO.readResource("/1CC8.ss.pdb");

				// 540 confs
				Strand design = new Strand.Builder(pdb)
					.setResidues("A68", "A73")
					.build();
				for (String resNum : Arrays.asList("A72", "A73")) { // gln(9+1) x leu(5+1) = 60
					design.flexibility.get(resNum)
						.setLibraryRotamers(Strand.WildType)
						.addWildTypeRotamers()
						.setContinuous();
				}

				Strand target = new Strand.Builder(pdb)
					.setResidues("A2", "A67")
					.build();
				for (String resNum : Arrays.asList("A6")) { // his(8+1) = 9
					target.flexibility.get(resNum)
						.setLibraryRotamers(Strand.WildType)
						.addWildTypeRotamers()
						.setContinuous();
				}

				// make a multi-state conf space
				return new MultiStateConfSpace
					.Builder("complex", new SimpleConfSpace.Builder().addStrands(design, target).build())
					.addMutableState("design", new SimpleConfSpace.Builder().addStrands(design).build())
					.addUnmutableState("target", new SimpleConfSpace.Builder().addStrands(target).build())
					.build();
			}
		},

		Stability1CC8Mut3 {

			@Override
			public MultiStateConfSpace make() {

				Molecule pdb = PDBIO.readResource("/1CC8.ss.pdb");

				// 729 confs
				Strand design = new Strand.Builder(pdb)
					.setResidues("A68", "A73")
					.build();
				for (String resNum : Arrays.asList("A71", "A72", "A73")) { // 9^3 = 729
					design.flexibility.get(resNum)
						.setLibraryRotamers("VAL", "LEU") // val(3) + leu(5) + 1 = 9
						.addWildTypeRotamers()
						.setContinuous();
				}

				// make a multi-state conf space
				return new MultiStateConfSpace
					.Builder("design", new SimpleConfSpace.Builder().addStrands(design).build())
					.build();
			}
		},

		Binding1CC8Mut2Flex1 {

			@Override
			public MultiStateConfSpace make() {

				Molecule pdb = PDBIO.readResource("/1CC8.ss.pdb");

				// 729 confs
				Strand design = new Strand.Builder(pdb)
					.setResidues("A68", "A73")
					.build();
				for (String resNum : Arrays.asList("A72", "A73")) { // 9^2 = 81
					design.flexibility.get(resNum)
						.setLibraryRotamers("VAL", "LEU") // val(3) + leu(5) + wt(1) = 9
						.addWildTypeRotamers()
						.setContinuous();
				}

				Strand target = new Strand.Builder(pdb)
					.setResidues("A2", "A67")
					.build();
				for (String resNum : Arrays.asList("A6")) { // his(8) + wt(1) = 9
					target.flexibility.get(resNum)
						.setLibraryRotamers(Strand.WildType)
						.addWildTypeRotamers()
						.setContinuous();
				}

				// make a multi-state conf space
				return new MultiStateConfSpace
					.Builder("complex", new SimpleConfSpace.Builder().addStrands(design, target).build())
					.addMutableState("design", new SimpleConfSpace.Builder().addStrands(design).build())
					.addUnmutableState("target", new SimpleConfSpace.Builder().addStrands(target).build())
					.build();
			}
		},

		Binding1CC8Mut2Flex3 { // too big to brute force

			@Override
			public MultiStateConfSpace make() {

				Molecule pdb = PDBIO.readResource("/1CC8.ss.pdb");

				// 183,708 confs
				Strand design = new Strand.Builder(pdb)
					.setResidues("A68", "A73")
					.build();
				for (String resNum : Arrays.asList("A72", "A73")) { // 9^2 = 81
					design.flexibility.get(resNum)
						.setLibraryRotamers("VAL", "LEU") // val(3) + leu(5) + wt(1) = 9
						.addWildTypeRotamers()
						.setContinuous();
				}

				Strand target = new Strand.Builder(pdb)
					.setResidues("A2", "A67")
					.build();
				for (String resNum : Arrays.asList("A5", "A6", "A7")) { // lys(27+1) x his(8+1) x tyr(8+1) = 2268
					target.flexibility.get(resNum)
						.setLibraryRotamers(Strand.WildType)
						.addWildTypeRotamers()
						.setContinuous();
				}

				// make a multi-state conf space
				return new MultiStateConfSpace
					.Builder("complex", new SimpleConfSpace.Builder().addStrands(design, target).build())
					.addMutableState("design", new SimpleConfSpace.Builder().addStrands(design).build())
					.addUnmutableState("target", new SimpleConfSpace.Builder().addStrands(target).build())
					.build();
			}
		};

		private static final Map<ConfSpaces,MultiStateConfSpace> confSpaces = new EnumMap<>(ConfSpaces.class);

		public abstract MultiStateConfSpace make();

		public MultiStateConfSpace get() {
			return confSpaces.computeIfAbsent(this, key -> make());
		}
	}

	private static enum Designs {

		// WARNING: if you change any details here, make sure to delete any cached files in `TestSofea.tmpdir`

		Binding1CC8Flex3_Traditional {
			@Override
			public Design make() {
				return new Design(this, ConfSpaces.Binding1CC8Flex3.get(), EnergyPartition.Traditional);
			}
		},
		Stability1CC8Mut3_Traditional {
			@Override
			public Design make() {
				return new Design(this, ConfSpaces.Stability1CC8Mut3.get(), EnergyPartition.Traditional);
			}
		},
		Binding1CC8Mut2Flex1_Traditional {
			@Override
			public Design make() {
				return new Design(this, ConfSpaces.Binding1CC8Mut2Flex1.get(), EnergyPartition.Traditional);
			}
		},
		Binding1CC8Mut2Flex3_Traditional {
			@Override
			public Design make() {
				return new Design(this, ConfSpaces.Binding1CC8Mut2Flex3.get(), EnergyPartition.Traditional);
			}
		},
		Binding1CC8Mut2Flex1_AllOnPairs {
			@Override
			public Design make() {
				return new Design(this, ConfSpaces.Binding1CC8Mut2Flex1.get(), EnergyPartition.AllOnPairs);
			}
		};


		private static final Map<Designs,Design> designs = new EnumMap<>(Designs.class);

		public abstract Design make();

		public Design get() {
			return designs.computeIfAbsent(this, key -> make());
		}
	}

	private static class Design {

		public final Designs id;
		public final MultiStateConfSpace confSpace;
		public final EnergyPartition epart;
		public final EnergyMatrix[] ematsLower;
		public final EnergyMatrix[] ematsUpper;

		public Design(Designs id, MultiStateConfSpace confSpace, EnergyPartition epart) {

			this.id = id;
			this.confSpace = confSpace;
			this.epart = epart;

			// calc the emats
			ematsLower = new EnergyMatrix[confSpace.states.size()];
			ematsUpper = new EnergyMatrix[confSpace.states.size()];
			try (Ecalcs ecalcs = makeEcalcs()) {
				for (MultiStateConfSpace.State state : confSpace.states) {
					ematsLower[state.index] = new SimplerEnergyMatrixCalculator.Builder(ecalcs.getMinimizing(state))
						.setCacheFile(new File(tmpdir, String.format("%s.%s.emat.lower", id, state.name)))
						.build()
						.calcEnergyMatrix();
					ematsUpper[state.index] = new SimplerEnergyMatrixCalculator.Builder(ecalcs.getRigid(state))
						.setCacheFile(new File(tmpdir, String.format("%s.%s.emat.upper", id, state.name)))
						.build()
						.calcEnergyMatrix();
				}
			}
		}

		public Ecalcs makeEcalcs() {
			return new Ecalcs(confSpace, epart);
		}

		public Sofea.StateConfig configState(MultiStateConfSpace.State state, Ecalcs ecalcs) {
			return new Sofea.StateConfig(
				ematsLower[state.index],
				ematsUpper[state.index],
				ecalcs.getMinimizing(state),
				new File(tmpdir, String.format("%s.%s.confdb", id, state.name))
			);
		}
	}

	private static class Ecalcs implements AutoCloseable {

		final MultiStateConfSpace confSpace;
		final EnergyCalculator minimizingEcalc;
		final EnergyCalculator rigidEcalc;
		final ConfEnergyCalculator[] minimizingConfEcalcs;
		final ConfEnergyCalculator[] rigidConfEcalcs;

		public Ecalcs(MultiStateConfSpace confSpace, EnergyPartition epart) {

			this.confSpace = confSpace;

			minimizingEcalc = new EnergyCalculator.Builder(confSpace, new ForcefieldParams())
				.setParallelism(fullCPUParallelism)
				.build();

			rigidEcalc = new EnergyCalculator.SharedBuilder(minimizingEcalc)
				.setIsMinimizing(false)
				.build();

			minimizingConfEcalcs = new ConfEnergyCalculator[confSpace.states.size()];
			rigidConfEcalcs = new ConfEnergyCalculator[confSpace.states.size()];
			for (MultiStateConfSpace.State state : confSpace.states) {
				minimizingConfEcalcs[state.index] = new ConfEnergyCalculator.Builder(state.confSpace, minimizingEcalc)
					.setEnergyPartition(epart)
					.build();
				rigidConfEcalcs[state.index] = new ConfEnergyCalculator.Builder(state.confSpace, rigidEcalc)
					.setEnergyPartition(epart)
					.build();
			}
		}

		@Override
		public void close() {
			minimizingEcalc.close();
		}

		public ConfEnergyCalculator getMinimizing(MultiStateConfSpace.State state) {
			return minimizingConfEcalcs[state.index];
		}

		public ConfEnergyCalculator getRigid(MultiStateConfSpace.State state) {
			return rigidConfEcalcs[state.index];
		}

		public void waitForFinish() {
			minimizingEcalc.tasks.waitForFinish();
		}
	}
}
