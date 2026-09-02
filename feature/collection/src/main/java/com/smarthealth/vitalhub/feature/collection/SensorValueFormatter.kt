package com.smarthealth.vitalhub.feature.collection

import java.util.Locale

internal fun Double.formatSensorHundredths(): String = String.format(Locale.US, "%.2f", this)
