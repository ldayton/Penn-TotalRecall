package control;

/**
 * Linux-specific platform implementation. For now, inherits all PC behavior. Could override for
 * Linux-specific differences.
 */
public class LinuxPlatform extends PCPlatform {
    // Inherits all behavior from PCPlatform
    // Could override getKeySymbol to show "Super" instead of "Meta" if needed
}
