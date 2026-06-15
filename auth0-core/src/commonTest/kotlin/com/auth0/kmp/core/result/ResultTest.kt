package com.auth0.kmp.core.result

import com.auth0.kmp.core.error.Auth0Error
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

private data class FakeError(val reason: String) : Auth0Error

class ResultTest {

    @Test
    fun getOrNull_returnsData_onSuccess() {
        val result: Result<Int, FakeError> = Result.Success(42)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun getOrNull_returnsNull_onFailure() {
        val result: Result<Int, FakeError> = Result.Failure(FakeError("boom"))
        assertNull(result.getOrNull())
    }

    @Test
    fun fold_invokesOnSuccess_onSuccess() {
        val result: Result<Int, FakeError> = Result.Success(7)
        var seen: Int? = null
        result.fold(onSuccess = { seen = it }, onFailure = { fail("should not fail") })
        assertEquals(7, seen)
    }

    @Test
    fun fold_invokesOnFailure_onFailure() {
        val error = FakeError("nope")
        val result: Result<Int, FakeError> = Result.Failure(error)
        var seen: FakeError? = null
        result.fold(onSuccess = { fail("should not succeed") }, onFailure = { seen = it })
        assertEquals(error, seen)
    }

    @Test
    fun map_transformsData_onSuccess() {
        val result: Result<Int, FakeError> = Result.Success(2)
        val mapped = result.map { it * 10 }
        assertEquals(Result.Success(20), mapped)
    }

    @Test
    fun map_passesThroughError_onFailure() {
        val error = FakeError("x")
        val result: Result<Int, FakeError> = Result.Failure(error)
        val mapped = result.map { it * 10 }
        assertEquals(Result.Failure(error), mapped)
    }

    @Test
    fun flatMap_chains_onSuccess() {
        val result: Result<Int, FakeError> = Result.Success(3)
        val chained = result.flatMap { Result.Success(it + 1) }
        assertEquals(Result.Success(4), chained)
    }

    @Test
    fun flatMap_shortCircuits_onFailure() {
        val error = FakeError("stop")
        val result: Result<Int, FakeError> = Result.Failure(error)
        val chained = result.flatMap { Result.Success(it + 1) }
        assertEquals(Result.Failure(error), chained)
    }
}
