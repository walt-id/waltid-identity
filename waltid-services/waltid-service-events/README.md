# waltid-service-events

The event types the walt.id services record: issuance, verification, key, DID and wallet events, and the filters to
query them (`id.walt.events`).

Moved out of `waltid-service-commons`, which is shared by every service, since only services that record events need
them. The event classes keep their former class names (`id.walt.commons.events.*`) as serial names, so events stored or
exported before the move stay readable.
