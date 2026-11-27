package id.waltid.openid4vp.wallet

/** A submitted protocol response may have reached the verifier before its reply was available. */
class PresentationDeliveryUncertainException(cause: Throwable) :
    Exception("Delivery could not be confirmed. Check the verifier transaction before sharing again.", cause)
