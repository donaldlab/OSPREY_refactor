
#ifndef CONFECALC_ASSIGNMENT_H
#define CONFECALC_ASSIGNMENT_H


namespace osprey {

	template<typename T>
	class Assignment {
		public:

			__host__ __device__
			static int64_t sizeof_index_offsets(int num_pos) {
				return num_pos*sizeof(int32_t);
			}

			__host__ __device__
			static int64_t sizeof_atom_pairs(int num_pos) {
				return (1 + 2*num_pos + num_pos*(num_pos - 1)/2)*sizeof(void *);
			}

			__host__ __device__
			static int64_t sizeof_conf_energies(int num_pos) {
				return num_pos*sizeof(T);
			}

			__device__
			Assignment(const ConfSpace<T> & conf_space, const Array<int32_t> & conf, Array<Real3<T>> & atoms,
			           int32_t * shared_index_offsets, const void * shared_atom_pairs[], T shared_conf_energies[])
				: conf_space(conf_space), conf(conf), atoms(atoms), index_offsets(shared_index_offsets), atom_pairs(shared_atom_pairs), conf_energies(shared_conf_energies) {

				int32_t offset = 0;

				// copy the static atoms
				offset += atoms.copy_from_device(conf_space.get_static_atom_coords(), offset);
				if (threadIdx.x == 0) {
					shared_atom_pairs[conf_space.index_static_static()] = conf_space.get_static_static_pair();
				}

				for (int posi1=0; posi1<conf_space.num_pos; posi1++) {
					const Pos & pos1 = conf_space.get_pos(posi1);

					int64_t num_copied = 0;

					// save the offset for this position, for later lookups
					index_offsets[posi1] = offset;

					// is this pos assigned?
					int32_t confi1 = conf[posi1];
					if (confi1 >= 0) {
						const Conf<T> & pconf1 = conf_space.get_conf(pos1, confi1);

						// yup, copy the atoms
						num_copied = atoms.copy_from_device(conf_space.get_conf_atom_coords(pconf1), offset);
						offset += num_copied;

						if (threadIdx.x == 0) {

							// collect the conf internal energies
							shared_conf_energies[posi1] = pconf1.internal_energy;

							// set the atom pair pointers
							shared_atom_pairs[conf_space.index_static_pos(posi1)] = conf_space.get_static_pos_pairs(posi1, pconf1.frag_index);
							shared_atom_pairs[conf_space.index_pos(posi1)] = conf_space.get_pos_pairs(posi1, pconf1.frag_index);
						}

						for (int posi2=threadIdx.x; posi2<posi1; posi2+=blockDim.x) {
							const Pos & pos2 = conf_space.get_pos(posi2);

							// is pos2 assigned?
							int32_t confi2 = conf[posi2];
							if (confi2 >= 0) {
								const Conf<T> & pconf2 = conf_space.get_conf(pos2, confi2);

								// yup, get the atom pairs
								shared_atom_pairs[conf_space.index_pos_pos(posi1, posi2)] = conf_space.get_pos_pos_pairs(posi1, pconf1.frag_index, posi2, pconf2.frag_index);
							}
						}
					}

					// zero out the rest of the space for this pos
					int64_t atoms_remaining = pos1.max_num_atoms - num_copied;
					atoms.fill_device(offset, atoms_remaining, Real3<T> { 0.0, 0.0, 0.0 });
					offset += atoms_remaining;
				}
				__syncthreads();

				atom_pairs = shared_atom_pairs;
				conf_energies = shared_conf_energies;
			}

			__device__
			Assignment(const Assignment & other) = delete;

			~Assignment() = default;

			__device__
			inline const void * get_atom_pairs(int posi1, int posi2) const {
				return atom_pairs[conf_space.index(posi1, posi2)];
			}

			__device__
			inline T get_conf_energy(int posi) const {
				return conf_energies[posi];
			}

			__device__
			inline int64_t get_static_index(int atomi) const {
				// static atoms are first in the array
				return atomi;
			}

			__device__
			inline int64_t get_index(int posi, int atomi) const {
				return index_offsets[posi] + atomi;
			}

			const ConfSpace<T> & conf_space;
			const Array<int32_t> & conf;
			Array<Real3<T>> & atoms;

		private:
			int32_t * index_offsets;
			const void * const * atom_pairs;
			const T * conf_energies;
	};
}


#endif //CONFECALC_ASSIGNMENT_H
