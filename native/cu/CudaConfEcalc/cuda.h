
#ifndef CUDACONFECALC_CUDA_H
#define CUDACONFECALC_CUDA_H


#include <cooperative_groups.h>

namespace cg = cooperative_groups;


namespace osprey {

	// use cuda vector types for Real3

	template<typename T>
	struct Real3Map {
		typedef void type;
		const static size_t size;
	};

	template<>
	struct Real3Map<float32_t> {
		typedef float3 type;

		// yes, a float3 is only 12 bytes,
		// but actually loading exactly 3 floats requires 2 load instructions
		// eg in PTX: ld.global.v2.f32 and ld.global.f32
		// so pretend a float3 is 16 bytes so we can use 1 load instruction
		// eg in PTX: ld.global.v4.f32
		const static size_t size = 16;
	};

	template<>
	struct Real3Map<float64_t> {
		typedef double3 type;
		const static size_t size = 24;
	};

	template<typename T>
	using Real3 = typename Real3Map<T>::type;

	// these are the sizes and alignments the compiler actually uses
	static_assert(sizeof(Real3<float32_t>) == 12);
	static_assert(alignof(Real3<float32_t>) == 4);

	static_assert(sizeof(Real3<float64_t>) == 24);
	static_assert(alignof(Real3<float64_t>) == 8);


	// add factory methods for Real3

	template<typename T>
	__device__
	inline Real3<T> real3(const T & x, const T & y, const T & z);

	template<>
	__device__
	inline float3 real3<float32_t>(const float32_t & x, const float32_t & y, const float32_t & z) {
		return make_float3(x, y, z);
	}

	template<>
	__device__
	inline double3 real3<float64_t>(const float64_t & x, const float64_t & y, const float64_t & z) {
		return make_double3(x, y, z);
	}

	template<typename T>
	__device__
	inline Real3<T> real3(const int & x, const int & y, const int & z);

	template<>
	__device__
	inline float3 real3<float32_t>(const int & x, const int & y, const int & z) {
		return real3(
			static_cast<float32_t>(x),
			static_cast<float32_t>(y),
			static_cast<float32_t>(z)
		);
	}

	template<>
	__device__
	inline double3 real3<float64_t>(const int & x, const int & y, const int & z) {
		return real3(
			static_cast<float64_t>(x),
			static_cast<float64_t>(y),
			static_cast<float64_t>(z)
		);
	}


	// add math functions for vector types, since CUDA apparently doesn't have them in the stdlib ;_;

	template<typename T>
	__device__
	inline T dot(const Real3<T> & a, const Real3<T> & b) {
		return a.x*b.x + a.y*b.y + a.z*b.z;
	}

	template<typename T>
	__device__
	inline T distance_sq(const Real3<T> & a, const Real3<T> & b) {
		T dx = a.x - b.x;
		T dy = a.y - b.y;
		T dz = a.z - b.z;
		return dx*dx + dy*dy + dz*dz;
	}

	template<typename T>
	__device__
	inline void operator += (Real3<T> & self, const Real3<T> & other) {
		self.x += other.x;
		self.y += other.y;
		self.z += other.z;
	}

	// nvcc can't find the templated operator for some reason, so explicitly instantiate it here
	__device__
	inline void operator += (float3 & self, const float3 & other) {
		operator +=<float32_t>(self, other);
	}
	__device__
	inline void operator += (double3 & self, const double3 & other) {
		operator +=<float64_t>(self, other);
	}


	template<typename T>
	__device__
	inline void operator -= (Real3<T> & self, const Real3<T> & other) {
		self.x -= other.x;
		self.y -= other.y;
		self.z -= other.z;
	}

	// nvcc can't find the templated operator for some reason, so explicitly instantiate it here
	__device__
	inline void operator -= (float3 & self, const float3 & other) {
		operator -=<float32_t>(self, other);
	}
	__device__
	inline void operator -= (double3 & self, const double3 & other) {
		operator -=<float64_t>(self, other);
	}

	template<typename T>
	__device__
	inline Real3<T> operator - (const Real3<T> & v) {
		return {
			-v.x,
			-v.y,
			-v.z
		};
	}

	// nvcc can't find the templated operator for some reason, so explicitly instantiate it here
	__device__
	inline Real3<float32_t> operator - (const float3 & v) {
		return operator -<float32_t>(v);
	}
	__device__
	inline Real3<float64_t> operator - (const double3 & v) {
		return operator -<float64_t>(v);
	}

	template<typename T>
	__device__
	inline Real3<T> operator + (const Real3<T> & a, const Real3<T> & b) {
		return {
			a.x + b.x,
			a.y + b.y,
			a.z + b.z
		};
	}

	// nvcc can't find the templated operator for some reason, so explicitly instantiate it here
	__device__
	inline float3 operator + (const float3 & a, const float3 & b) {
		return operator +<float32_t>(a, b);
	}
	__device__
	inline double3 operator + (const double3 & a, const double3 & b) {
		return operator +<float64_t>(a, b);
	}

	template<typename T>
	__device__
	inline Real3<T> operator - (const Real3<T> & a, const Real3<T> & b) {
		return {
			a.x - b.x,
			a.y - b.y,
			a.z - b.z
		};
	}

	// nvcc can't find the templated operator for some reason, so explicitly instantiate it here
	__device__
	inline float3 operator - (const float3 & a, const float3 & b) {
		return operator -<float32_t>(a, b);
	}
	__device__
	inline double3 operator - (const double3 & a, const double3 & b) {
		return operator -<float64_t>(a, b);
	}

	template<typename T>
	__device__
	inline Real3<T> cross(const Real3<T> & a, const Real3<T> & b) {
		return {
			a.y*b.z - a.z*b.y,
			a.z*b.x - a.x*b.z,
			a.x*b.y - a.y*b.x
		};
	}

	template<typename T>
	__device__
	inline T len_sq(const Real3<T> & v) {
		return v.x*v.x + v.y*v.y + v.z*v.z;
	}

	template<typename T>
	__device__
	inline T len(const Real3<T> & v) {
		return std::sqrt(len_sq<T>(v));
	}

	template<typename T>
	__device__
	inline void normalize(Real3<T> & v) {
		T invlen = 1.0/len<T>(v);
		v.x *= invlen;
		v.y *= invlen;
		v.z *= invlen;
	}

	template<typename T>
	__device__
	inline bool isnan3(const Real3<T> & v) {
		return isnan<T>(v.x) || isnan<T>(v.y) || isnan<T>(v.z);
	}
}


namespace cuda {

	__host__
	void check_error();

	__host__
	int optimize_threads_void(const void * func, size_t shared_size_static, size_t shared_size_per_thread);

	// pick the greatest number of the threads that keeps occupancy above 0
	template<typename T>
	int optimize_threads(const T & func, size_t shared_size_static, size_t shared_size_per_thread) {
		return optimize_threads_void(reinterpret_cast<const void *>(&func), shared_size_static, shared_size_per_thread);
	}

	__device__
	int tile_rank(cg::thread_group parent, cg::thread_group child);
	__device__
	int num_tiles(cg::thread_group parent, cg::thread_group child);

	__host__ __device__
	int64_t pad_to_alignment(int64_t size, int64_t alignment);
}


#define PRINTF0(threads, fmt, ...) \
	if (threads.thread_rank() == 0) { \
		printf(fmt, __VA_ARGS__); \
	}


#endif //CUDACONFECALC_CUDA_H
