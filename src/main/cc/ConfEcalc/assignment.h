
#ifndef CONFECALC_ASSIGNMENT_H
#define CONFECALC_ASSIGNMENT_H


namespace osprey {

	template<typename T>
	class Assignment {

		public:
			Assignment(const ConfSpace<T> & _conf_space, const int32_t _conf[])
				: conf_space(_conf_space), conf(_conf), atoms(_conf_space.max_num_conf_atoms) {

				int32_t offset = 0;
				auto _index_offsets = new int64_t[conf_space.num_pos];
				auto _atom_pairs = new const void *[1 + 2*conf_space.num_pos + conf_space.num_pos*(conf_space.num_pos - 1)/2];
				auto _conf_energies = new T[conf_space.num_pos];

				// copy the static atoms
				offset += atoms.copy_from(conf_space.get_static_atom_coords());
				_atom_pairs[conf_space.index_static_static()] = conf_space.get_static_static_pair();

				for (int posi1=0; posi1<conf_space.num_pos; posi1++) {
					const Pos & pos1 = conf_space.get_pos(posi1);

					int64_t num_copied = 0;

					// save the offset for this position, for later lookups
					_index_offsets[posi1] = offset;

					// is pos1 assigned?
					int32_t confi1 = conf[posi1];
					if (confi1 >= 0) {
						const Conf<T> & pconf1 = conf_space.get_conf(pos1, confi1);

						// yup, copy the atoms
						num_copied = atoms.copy_from(conf_space.get_conf_atom_coords(pconf1), offset);
						offset += num_copied;

						// collect the conf internal energies
						_conf_energies[posi1] = pconf1.internal_energy;

						// set the atom pair pointers
						_atom_pairs[conf_space.index_static_pos(posi1)] = conf_space.get_static_pos_pairs(posi1, pconf1.frag_index);
						_atom_pairs[conf_space.index_pos(posi1)] = conf_space.get_pos_pairs(posi1, pconf1.frag_index);

						for (int posi2=0; posi2<posi1; posi2++) {
							const Pos & pos2 = conf_space.get_pos(posi2);

							// is pos2 assigned?
							int32_t confi2 = conf[posi2];
							if (confi2 >= 0) {
								const Conf<T> & pconf2 = conf_space.get_conf(pos2, conf[posi2]);

								// yup, get the atom pairs
								_atom_pairs[conf_space.index_pos_pos(posi1, posi2)] = conf_space.get_pos_pos_pairs(posi1, pconf1.frag_index, posi2, pconf2.frag_index);
							}
						}
					}

					// zero out the rest of the space for this pos
					int64_t atoms_remaining = pos1.max_num_atoms - num_copied;
					if (atoms_remaining > 0) {
						std::memset(&atoms[offset], 0, sizeof(Real3<T>)*atoms_remaining);
					}
					offset += atoms_remaining;
				}

				index_offsets = _index_offsets;
				atom_pairs = _atom_pairs;
				conf_energies = _conf_energies;
			}

			Assignment(const Assignment & other) = delete;

			~Assignment() {
				delete[] index_offsets;
				delete[] atom_pairs;
				delete[] conf_energies;
			}

			inline const void * get_atom_pairs(int posi1, int posi2) const {
				return atom_pairs[conf_space.index(posi1, posi2)];
			}

			inline T get_conf_energy(int posi) const {
				return conf_energies[posi];
			}

			inline int64_t get_static_index(int atomi) const {
				// static atoms are first in the array
				return atomi;
			}

			inline int64_t get_index(int posi, int atomi) const {
				return index_offsets[posi] + atomi;
			}

			const ConfSpace<T> & conf_space;
			const int32_t * conf;
			Array<Real3<T>> atoms;

		private:
			const int64_t * index_offsets;
			const void ** atom_pairs;
			const T * conf_energies;
	};
}


#endif //CONFECALC_ASSIGNMENT_H
