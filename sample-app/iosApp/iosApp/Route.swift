//
//  Route.swift
//  iosApp
//
//  Navigation destinations for the NavigationStack. Mirrors the Android sample's
//  AppRoute. The chooser is the stack's root, so it is not a case here — only the
//  destinations pushed on top of it are. No case carries a payload: credentials
//  flow through the shared AuthViewModel, not through navigation values.
//

enum Route: Hashable {
    case embeddedLogin
    case welcome
}
