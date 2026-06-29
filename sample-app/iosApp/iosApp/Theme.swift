//
//  Theme.swift
//  iosApp
//
//  Lightweight design tokens modeled on Auth0's ui-components-ios reference.
//  A single login screen does not need the full token-system machinery, so we
//  keep the brand look (palette, spacing, corners, sizes) in one place.
//

import SwiftUI

enum Spacing {
    static let xs: CGFloat = 8
    static let sm: CGFloat = 12
    static let md: CGFloat = 16
    static let lg: CGFloat = 24
    static let xl: CGFloat = 32
}

enum Sizes {
    static let buttonHeight: CGFloat = 56
    static let inputHeight: CGFloat = 56
    static let cornerLarge: CGFloat = 16
    static let cornerInput: CGFloat = 14
}

extension Color {
    // Brand palette adapts to light/dark, mirroring the reference's adaptive
    // asset-catalog colors (near-black primary that inverts in dark mode).
    static let brandPrimary = Color(
        light: Color(red: 0.035, green: 0.035, blue: 0.043),   // #09090B
        dark: Color(red: 0.98, green: 0.98, blue: 0.98)        // #FAFAFA
    )
    static let brandOnPrimary = Color(
        light: Color(red: 0.94, green: 0.94, blue: 0.94),      // #F0F0F0
        dark: Color(red: 0.094, green: 0.094, blue: 0.106)     // #18181B
    )
    static let brandBorder = Color(
        light: Color(red: 0.85, green: 0.85, blue: 0.85),      // #D9D9D9
        dark: Color(red: 0.247, green: 0.247, blue: 0.275)     // #3F3F46
    )
}

private extension Color {
    init(light: Color, dark: Color) {
        self = Color(UIColor { traits in
            traits.userInterfaceStyle == .dark ? UIColor(dark) : UIColor(light)
        })
    }
}

// Rounded, bordered text-field chrome shared by every input on the login screen,
// mirroring the reference's outlined-input look.
struct InputFieldStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .frame(height: Sizes.inputHeight)
            .padding(.horizontal, Spacing.md)
            .overlay(
                RoundedRectangle(cornerRadius: Sizes.cornerInput)
                    .stroke(Color.brandBorder, lineWidth: 1)
            )
    }
}
