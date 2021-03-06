/*
** This file is part of OSPREY 3.0
** 
** OSPREY Protein Redesign Software Version 3.0
** Copyright (C) 2001-2018 Bruce Donald Lab, Duke University
** 
** OSPREY is free software: you can redistribute it and/or modify
** it under the terms of the GNU General Public License version 2
** as published by the Free Software Foundation.
** 
** You should have received a copy of the GNU General Public License
** along with OSPREY.  If not, see <http://www.gnu.org/licenses/>.
** 
** OSPREY relies on grants for its development, and since visibility
** in the scientific literature is essential for our success, we
** ask that users of OSPREY cite our papers. See the CITING_OSPREY
** document in this distribution for more information.
** 
** Contact Info:
**    Bruce Donald
**    Duke University
**    Department of Computer Science
**    Levine Science Research Center (LSRC)
**    Durham
**    NC 27708-0129
**    USA
**    e-mail: www.cs.duke.edu/brd/
** 
** <signature of Bruce Donald>, Mar 1, 2018
** Bruce Donald, Professor of Computer Science
*/

package edu.duke.cs.osprey.structure;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.duke.cs.osprey.control.Main;
import edu.duke.cs.osprey.energy.EnergyCalculator;
import edu.duke.cs.osprey.energy.compiled.ConfEnergyCalculator;
import edu.duke.cs.osprey.tools.HashCalculator;
import edu.duke.cs.osprey.tools.VectorAlgebra;
import org.apache.commons.lang3.text.WordUtils;

import edu.duke.cs.osprey.structure.Residue.SecondaryStructure;
import edu.duke.cs.osprey.tools.FileTools;


/**
 * this is a clean PDB reader that doesn't know anything about templates or bonds
 * its only job is to turn PDB text into a molecule representing a polymer with residues and atoms
 */
public class PDBIO {
	
	private static class ResInfo {
		
		public String name = null;
		
		private ArrayList<Atom> atoms = new ArrayList<>();
		private ArrayList<double[]> coords = new ArrayList<>();
		private ArrayList<Character> alts = new ArrayList<>();
		
		public void clear() {
			name = null;
			atoms.clear();
			coords.clear();
			alts.clear();
		}
		
		public void addAtom(Atom atom, double x, double y, double z, char alt) {
			atoms.add(atom);
			coords.add(new double[] { x, y, z });
			alts.add(alt);
		}
		
		public void flush(Molecule mol) {
			
			assert (atoms.size() == coords.size());
			assert (atoms.size() == alts.size());
			
			if (atoms.isEmpty()) {
				clear();
				return;
			}
			
			// collect the unique alt names
			TreeSet<Character> altNames = new TreeSet<>(alts);
			altNames.remove(' ');
			
			// pick the main alt
			char mainAlt;
			if (altNames.isEmpty()) {
				mainAlt = ' ';
			} else {
				mainAlt = altNames.first();
				altNames.remove(mainAlt);
			}
			
			// make the main residue
			Residue mainRes = makeResidue(mainAlt, mol);
			mol.appendResidue(mainRes);
			
			// add the alt residues, if any
			for (Character alt : altNames) {
				mol.addAlternate(mainRes.indexInMolecule, makeResidue(alt, mol));
			}
			
			clear();
		}

		private Residue makeResidue(char alt, Molecule mol) {
			
			ArrayList<Atom> resAtoms = new ArrayList<>();
			ArrayList<double[]> resCoords = new ArrayList<>();
			
			// pick the atoms,coords that match the main or this alt
			for (int i=0; i<atoms.size(); i++) {
				char atomAlt = alts.get(i);
				if (atomAlt == ' ' || atomAlt == alt) {
					resAtoms.add(atoms.get(i).copy());
					resCoords.add(coords.get(i));
				}
			}
			
			return new Residue(resAtoms, resCoords, name, mol);
		}
	}
	
	public static Molecule readFile(String path) {
		return read(FileTools.readFile(path));
	}
	
	public static Molecule readFile(File file) {
		return read(FileTools.readFile(file));
	}

	public static Molecule readResource(String path) {
		return read(FileTools.readResource(path));
	}
	
	public static Molecule read(String pdbText) {
		return readAll(pdbText).get(0);
	}
	
	public static List<Molecule> readAll(String pdbText) {
		List<Molecule> mols = readMols(pdbText);
		readSecondaryStructure(mols, pdbText);
		return mols;
	}
	
	public static List<Molecule> readMols(String pdbText) {
		return readMols(FileTools.parseLines(pdbText));
	}

	/**
	 * faster reading method for bulk reads
	 * doesn't do secondary structure annotation
	 */
	public static List<Molecule> readMols(File file) {
		try (FileReader reader = new FileReader(file)) {
			return readMols(FileTools.parseLinesFast(reader));
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	private static List<Molecule> readMols(Iterable<String> pdbIter) {
		
		List<Molecule> mols = new ArrayList<>();
		Molecule mol = new Molecule();
		mols.add(mol);
		ResInfo resInfo = new ResInfo();
		
		for (String line : pdbIter) {
			line = padLine(line);
			
			if (isLine(line, "MODEL")) {
				
				// is this the first model?
				if (mol.residues.isEmpty()) {
					// ignore
				} else {
					// advance to the next molecule
					resInfo.flush(mol);
					mol = new Molecule();
					mols.add(mol);
				}
				
			} else if (isLine(line, "ATOM") || isLine(line, "HETATM")) {
				
				// eg
				//           1         2         3         4         5         6         7         8
				// 012345678901234567890123456789012345678901234567890123456789012345678901234567890
				// ATOM   1146  CB APRO A  38       5.781  17.860   0.637  0.45 12.10           C
				
				// parse the line
				int atomNum = Integer.parseInt(line.substring(6, 11).trim());
				String atomName = line.substring(12,16).trim();
				char alt = line.charAt(16);
				String resName = trimRight(line.substring(17,27));
				double x = Double.parseDouble(line.substring(30, 38).trim());
				double y = Double.parseDouble(line.substring(38, 46).trim());
				double z = Double.parseDouble(line.substring(46, 54).trim());
				double bFactor = Double.parseDouble(defaultVal("0", line.substring(60, 66).trim()));
				
				// read the element, but enforce proper capitalization so we can match to the names in PeriodicTable
				String elem = WordUtils.capitalize(line.substring(76, 78).trim().toLowerCase());
				
				// should we start a new residue (with alts)?
				if (!resName.equals(resInfo.name)) {
					resInfo.flush(mol);
					resInfo.name = resName;
				}
				
				// make the atom and check the element
				Atom atom;
				if (elem.isEmpty()) {
					atom = new Atom(atomName);
				} else {
					atom = new Atom(atomName, elem);
				}
				if (atom.elementType.equalsIgnoreCase("du")) {
					System.out.println(String.format("WARNING: Can't detect atom element: residue=%s, name=%s, element=%s\n"
						+ "\nPlease include element types in the PDB file to avoid this problem.",
						resInfo.name, atomName, elem
					));
				}
				
				// save the rest of the atom properties
				atom.BFactor = bFactor;
				atom.modelAtomNumber = atomNum;
				
				// update the res info with the atom
				resInfo.addAtom(atom, x, y, z, alt);
			}
		}
		
		resInfo.flush(mol);
		
		return mols;
	}
	
	private static String padLine(String line) {
		
		final int Len = 80;
		if (line.length() >= Len) {
			return line;
		}
		
		StringBuilder buf = new StringBuilder(Len);
		buf.append(line);
		while (buf.length() < Len) {
			buf.append(' ');
		}
		return buf.toString();
	}

	private static boolean isLine(String line, String type) {
		return line.regionMatches(true, 0, type, 0, type.length());
	}
	
	private static String defaultVal(String defaultVal, String inVal) {
		if (inVal == null || inVal.isEmpty()) {
			return defaultVal;
		}
		return inVal;
	}
	
	private static void readSecondaryStructure(List<Molecule> mols, String pdbText) {
		
		// NOTE: by default, residues are assigned LOOP secondary structure
		
		// parse pass 2: read the helices and sheets
		for (String line : FileTools.parseLines(pdbText)) {
			line = padLine(line);
			
			if (isLine(line, "HELIX")) {
				
				// eg
				//           1         2         3         4         5         6         7         8
				// 012345678901234567890123456789012345678901234567890123456789012345678901234567890
				// HELIX    1   1 ASN A    6  LEU A   19  1                                  14    
				// HELIX    2   2 THR A   23  ASP A   37  1                                  15    
				// HELIX    3   3 VAL A   40  GLN A   55  1                                  16    

				char chain = line.charAt(19);
				String startResNum = chain + line.substring(21, 25).trim();
				String stopResNum = chain + line.substring(33, 37).trim();
				
				for (Molecule mol : mols) {
					for (Residue res : mol.getResRangeByPDBResNumber(startResNum, stopResNum)) {
						if (res.getChainId() == chain) {
							res.secondaryStruct = SecondaryStructure.HELIX;
						}
					}
				}
				
			} else if (isLine(line, "SHEET")) {
				
				// eg
				//           1         2         3         4         5         6         7         8
				// 012345678901234567890123456789012345678901234567890123456789012345678901234567890
				// SHEET    1   A 4 VAL A  67  LEU A  73  0
				// SHEET    2   A 4 LYS A   5  VAL A  11 -1  N  ASN A  10   O  ARG A  68
				// SHEET    3   A 4 LEU A  44  THR A  49 -1  N  THR A  49   O  LYS A   5
				// SHEET    4   A 4 VAL A  33  SER A  39 -1  N  SER A  39   O  LEU A  44

				char chain = line.charAt(21);
				String startResNum = chain + line.substring(22, 26).trim();
				String stopResNum = chain + line.substring(33, 37).trim();
				
				for (Molecule mol : mols) {
					for (Residue res : mol.getResRangeByPDBResNumber(startResNum, stopResNum)) {
						if (res.getChainId() == chain) {
							res.secondaryStruct = SecondaryStructure.SHEET;
						}
					}
				}
			}	
		}
	}

	public static void writeFile(EnergyCalculator.EnergiedParametricMolecule epmol, String path) {
		writeFile(epmol, null, path);
	}

	public static void writeFile(EnergyCalculator.EnergiedParametricMolecule epmol, File file) {
		writeFile(epmol, null, file);
	}

	public static void writeFile(EnergyCalculator.EnergiedParametricMolecule epmol, String comment, String path) {
		FileTools.writeFile(write(epmol, comment), path);
	}

	public static void writeFile(EnergyCalculator.EnergiedParametricMolecule epmol, String comment, File file) {
		FileTools.writeFile(write(epmol, comment), file);
	}
	
	public static void writeFile(Molecule mol, String path) {
		FileTools.writeFile(write(mol), path);
	}
	
	public static void writeFile(Molecule mol, File file) {
		FileTools.writeFile(write(mol), file);
	}
	
	public static void writeFile(Molecule mol, String comment, Double energy, String path) {
		FileTools.writeFile(write(mol, comment, energy, false), path);
	}
	
	public static void writeFile(Molecule mol, String comment, Double energy, File file) {
		FileTools.writeFile(write(mol, comment, energy,false), file);
	}

	public static String write(EnergyCalculator.EnergiedParametricMolecule epmol) {
		return write(epmol, null);
	}

	public static String write(EnergyCalculator.EnergiedParametricMolecule epmol, String comment) {
		return write(epmol.pmol.mol, comment, epmol.energy, false);
	}
	
	public static String write(Molecule mol) {
		return write(mol, null, null, false);
	}

	public static String write(Molecule mol, String comment, Double energy, boolean includeTer) {
		return write(mol, comment, energy, includeTer, false);
	}

	public static String write(Molecule mol, String comment, Double energy, boolean includeTer, boolean includeSSBondConect) {
	
		StringBuilder buf = new StringBuilder();
		
		// write PDB headers (use REMARK 3 for refinement information)
		// http://www.wwpdb.org/documentation/file-format-content/format33/remark3.html
		buf.append("REMARK   3\n");
		buf.append("REMARK   3 Generated by OSPREY ");
		buf.append(Main.Version);
		buf.append("\n");
		buf.append("REMARK   3\n");
		if (comment != null) {
			buf.append("REMARK   3 COMMENT : ");
			buf.append(comment);
			buf.append("\n");
		}
		if (energy != null) {
			buf.append(String.format("REMARK   3 ENERGY  : %.6f\n", energy));
		}
		if (comment != null || energy != null) {
			buf.append("REMARK   3\n");
		}

		appendMol(buf, mol, includeTer, includeSSBondConect);

		buf.append("END\n");

		return buf.toString();
	}

	private static void appendMol(StringBuilder buf, Molecule mol, boolean includeTer, boolean includeSSBondConect) {

		// we'll use a char array to represent each line
		char[] line = new char[80];

		int atomCounter = 1;

		Map<Atom,Integer> atomIndices = new IdentityHashMap<>();

		for (Residue res : mol.residues) {
			for (Atom atom : res.atoms) {
				
				Arrays.fill(line, ' ');
				
				setField(line, "ATOM", 0, 5, Justify.Left);
				
				// write the residue name
				setField(line, res.fullName, 17, 26, Justify.Left);
			
				// always use full occupancy
				setField(line, "1.00", 56, 59, Justify.Left);
				
				// always use 0 for temp factor (b-factor)
				setField(line, "0.00", 62, 65, Justify.Left);

				// write the atom number
				atomIndices.put(atom, atomCounter);
				setField(line, atomCounter++, 6, 10, Justify.Right);
				
				// writing the atom name is a little fuzzy, although the
				//  atom name is allocated columns 12-15(zero based), rasmol
				//  likes and other people essentially start with column 13
				//  leaving column 12 blank. So we only allow 3 characters for
				//  the atom name and it should be left justified
				//  unless the first character is a number in which case we
				//  start with column 12
				// there are also exceptions when the atom has a two letter
				//  element code
				if (atom.name.length() >= 4 || Character.isDigit(atom.name.charAt(0))) {
					setField(line, atom.name, 12, 15, Justify.Left);
				} else {
					setField(line, atom.name, 13, 15, Justify.Left);
				}
				
				// write the coords
				setField(line, atom.getCoords()[0], 3, 30, 37, Justify.Right);
				setField(line, atom.getCoords()[1], 3, 38, 45, Justify.Right);
				setField(line, atom.getCoords()[2], 3, 46, 53, Justify.Right);
				
				// write the element
				setField(line, atom.elementType.toUpperCase(), 76, 77, Justify.Right);

				buf.append(line);
				buf.append("\n");
			}

			if(includeTer){//write TER to indicate subsequent residues not bonded.  Used by Sander
				if(res.indexInMolecule<mol.residues.size()-1){
					if(!res.isBondedTo(mol.residues.get(res.indexInMolecule+1)))
						buf.append("TER\n");
				}
			}
		}

		if (includeSSBondConect) {
			// include SSBOND and CONECT records in the PDB file, needed by some Amber tools

			// find all the cysteines
			List<Residue> cysteines = mol.residues.stream()
				.filter(res -> res.getType().equals("CYS") || res.getType().equals("CYX"))
				.collect(Collectors.toList());

			class SSBond {
				final Residue resa;
				final Atom atoma;
				final Residue resb;
				final Atom atomb;
				SSBond(Residue resa, Atom atoma, Residue resb, Atom atomb) {
					this.resa = resa;
					this.atoma = atoma;
					this.resb = resb;
					this.atomb = atomb;
				}
				@Override
				public int hashCode() {
					return HashCalculator.combineHashesCommutative(
						HashCalculator.combineObjHashes(resa, atoma),
						HashCalculator.combineObjHashes(resb, atomb)
					);
				}
				@Override
				public boolean equals(Object other) {
					return other instanceof SSBond && equals((SSBond)other);
				}
				public boolean equals(SSBond other) {
					return (this.resa == other.resa && this.atoma == other.atoma && this.resb == other.resb && this.atomb == other.atomb)
						|| (this.resa == other.resb && this.atoma == other.atomb && this.resb == other.resa && this.atomb == other.atoma);
				}
			}

			// find all the ssbonds
			List<SSBond> ssbonds = cysteines.stream()
				.flatMap(resa -> resa.atoms.stream()
					.filter(Atom::isSulfur)
					.flatMap(atoma -> atoma.bonds.stream()
						.filter(Atom::isSulfur)
						.map(atomb -> new SSBond(resa, atoma, atomb.res, atomb))
					)
				)
				.distinct()
				.sorted(Comparator.comparing(ssbond -> ssbond.resa.fullName))
				.collect(Collectors.toList());


			// render the ssbonds into PDB records
			int ssbondCounter = 0;
			for (SSBond ssbond : ssbonds) {

				Function<Residue,String> resname = res -> {
					String name = res.fullName.substring(5);
					if (name.length() == 4) {
						// add a blank insertion code if needed
						name = name + " ";
					}
					return name;
				};

				// write the SSBOND record, eg:
				//          1         2         3         4         5         6         7
				//01234567890123456789012345678901234567890123456789012345678901234567890123456789
				//SSBOND   1 CYS A    6    CYS A  127                          1555   1555  2.03
				// see: https://www.wwpdb.org/documentation/file-format-content/format33/sect6.html
				Arrays.fill(line, ' ');
				setField(line, "SSBOND", 0, 5, Justify.Left);
				setField(line, ssbondCounter++, 7, 9, Justify.Right);
				setField(line, ssbond.resa.getType(), 11, 13, Justify.Left);
				setField(line, ssbond.resa.getChainId(), 15);
				setField(line, resname.apply(ssbond.resa), 17, 21, Justify.Right);
				setField(line, ssbond.resb.getType(), 25, 27, Justify.Left);
				setField(line, ssbond.resb.getChainId(), 29);
				setField(line, resname.apply(ssbond.resb), 31, 35, Justify.Right);
				setField(line, 1555, 59, 64, Justify.Right);
				setField(line, 1555, 66, 71, Justify.Right);
				double dist = VectorAlgebra.distance(
					ssbond.resa.coords, ssbond.atoma.indexInRes,
					ssbond.resb.coords, ssbond.atomb.indexInRes
				);
				setField(line, dist, 2, 73, 77, Justify.Right);
				buf.append(line);
				buf.append("\n");

				// write two CONECT records, eg:
				//          1         2         3         4         5         6         7
				//01234567890123456789012345678901234567890123456789012345678901234567890123456789
				//CONECT 1179  746
				//CONECT  746 1179
				// see: https://www.wwpdb.org/documentation/file-format-content/format33/sect10.html
				Arrays.fill(line, ' ');
				setField(line, "CONECT", 0, 5, Justify.Left);
				setField(line, atomIndices.get(ssbond.atoma), 6, 10, Justify.Right);
				setField(line, atomIndices.get(ssbond.atomb), 11, 15, Justify.Right);
				buf.append(line);
				buf.append("\n");
				Arrays.fill(line, ' ');
				setField(line, "CONECT", 0, 5, Justify.Left);
				setField(line, atomIndices.get(ssbond.atomb), 6, 10, Justify.Right);
				setField(line, atomIndices.get(ssbond.atoma), 11, 15, Justify.Right);
				buf.append(line);
				buf.append("\n");
			}
		}
	}
	
	private static enum Justify {
		
		Left {
			@Override
			public String apply(String in, int size) {
				StringBuilder buf = new StringBuilder();
				buf.append(in);
				int pad = size - in.length();
				for (int i=0; i<pad; i++) {
					buf.append(' ');
				}
				return buf.toString();
			}
		},
		Right {
			@Override
			public String apply(String in, int size) {
				StringBuilder buf = new StringBuilder();
				int pad = size - in.length();
				for (int i=0; i<pad; i++) {
					buf.append(' ');
				}
				buf.append(in);
				return buf.toString();
			}
		};
		
		public abstract String apply(String in, int size);
	}

	private static void setField(char[] line, char value, int pos) {
		line[pos] = value;
	}

	private static void setField(char[] line, int field, int start, int stop, Justify justify) {
		setField(line, Integer.toString(field), start, stop, justify);
	}
	
	private static void setField(char[] line, double field, int precision, int start, int stop, Justify justify) {
		setField(line, String.format("%." + precision + "f", field), start, stop, justify);
	}
	
	private static void setField(char[] line, String field, int start, int stop, Justify justify) {
		int size = stop - start + 1;
		if (field.length() > size) {
			throw new IllegalArgumentException(String.format("value (%s) is too large for for field [%d,%d]", field, start, stop));
		} else if (field.length() != size) {
			field = justify.apply(field, size);
		}
		field.getChars(0, size, line, start);
	}
	
    private static String trimRight(String str){
        //modification of trim that only trims the right side
        int len = str.length();
        while( (len>0) && (str.charAt(len-1) <= ' ') )
            len--;
        return (len<str.length()) ? str.substring(0,len) : str;
    }

	public static void writeEnsemble(List<EnergyCalculator.EnergiedParametricMolecule> epmols, String filePattern) {

		if (epmols.isEmpty()) {
			return;
		}

		// the pattern has a * right?
		if (filePattern.indexOf('*') < 0) {
			throw new IllegalArgumentException("filePattern (which is '" + filePattern + "') has no wildcard character (which is *)");
		}

		// mkdirs
		new File(filePattern).getParentFile().mkdirs();

		// figure out how many characters to make the conf ids
		int indexSize = 1 + (int)Math.log10(epmols.size());
		String indexFormat = "%0" + indexSize + "d";

		for (int i=0; i<epmols.size(); i++) {
			String filename = filePattern.replace("*", String.format(indexFormat, i + 1));
			PDBIO.writeFile(epmols.get(i), filename);
		}
	}

	public static String write(List<Molecule> mols, List<Double> energies, String comment) {

		StringBuilder buf = new StringBuilder();

		// write PDB headers (use REMARK 3 for refinement information)
		// http://www.wwpdb.org/documentation/file-format-content/format33/remark3.html
		buf.append("REMARK   3\n");
		buf.append("REMARK   3 Generated by OSPREY ");
		buf.append(Main.Version);
		buf.append("\n");
		buf.append("REMARK   3\n");
		if (comment != null) {
			buf.append("REMARK   3 COMMENT : ");
			buf.append(comment);
			buf.append("\n");
		}
		for (double energy : energies) {
			buf.append(String.format("REMARK   3 ENERGY  : %.6f\n", energy));
		}
		buf.append("REMARK   3\n");

		int modelNum = 1;
		for (Molecule mol : mols) {
			buf.append(String.format("MODEL %4d\n", modelNum++));
			appendMol(buf, mol, false, false);
			buf.append("ENDMDL\n");
		}

		buf.append("END\n");

		return buf.toString();
	}

	/**
	 * Writes an ensemble of structures into a single PDB file
	 */
	public static String write(List<EnergyCalculator.EnergiedParametricMolecule> epmols, String comment) {
		return write(
			epmols.stream().map(epmol -> epmol.pmol.mol).collect(Collectors.toList()),
			epmols.stream().map(epmol -> epmol.energy).collect(Collectors.toList()),
			comment
		);
	}

	public static void writeFile(List<EnergyCalculator.EnergiedParametricMolecule> epmols, String path, String comment) {
		writeFile(epmols, new File(path), comment);
	}

	public static void writeFile(List<EnergyCalculator.EnergiedParametricMolecule> epmols, File file, String comment) {
		FileTools.writeFile(write(epmols, comment), file);
	}

	/**
	 * Writes an ensemble of structures into a single PDB file
	 */
	public static String writeEcoords(List<ConfEnergyCalculator.EnergiedCoords> ecoords, String comment) {
		return write(
			ecoords.stream().map(ecoord -> ecoord.coords.toMol()).collect(Collectors.toList()),
			ecoords.stream().map(ecoord -> ecoord.energy).collect(Collectors.toList()),
			comment
		);
	}

	public static void writeFileEcoords(List<ConfEnergyCalculator.EnergiedCoords> ecoords, String path, String comment) {
		writeFileEcoords(ecoords, new File(path), comment);
	}

	public static void writeFileEcoords(List<ConfEnergyCalculator.EnergiedCoords> ecoords, File file, String comment) {
		FileTools.writeFile(writeEcoords(ecoords, comment), file);
	}
}
