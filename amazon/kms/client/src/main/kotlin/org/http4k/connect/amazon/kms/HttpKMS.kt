package org.http4k.connect.amazon.kms

import org.http4k.aws.AwsCredentials
import org.http4k.client.JavaHttpClient
import org.http4k.connect.amazon.awsCredentials
import org.http4k.connect.amazon.awsRegion
import org.http4k.connect.amazon.core.model.Region
import org.http4k.connect.amazon.kms.action.KMSAction
import org.http4k.core.HttpHandler
import org.http4k.core.then
import org.http4k.filter.Payload
import java.time.Clock

fun KMS.Companion.Http(
    region: Region,
    credentialsProvider: () -> AwsCredentials,
    http: HttpHandler = JavaHttpClient(),
    clock: Clock = Clock.systemUTC()
) = object : KMS {
    private val signedHttp = signAwsRequests(region, credentialsProvider, clock, Payload.Mode.Signed).then(http)

    override fun <R : Any> invoke(action: KMSAction<R>) = action.toResult(signedHttp(action.toRequest()))
}

fun KMS.Companion.Http(
    env: Map<String, String> = System.getenv(),
    http: HttpHandler = JavaHttpClient(),
    clock: Clock = Clock.systemUTC()
) = Http(env.awsRegion(), env.awsCredentials(), http, clock)
