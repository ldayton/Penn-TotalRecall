#!/usr/bin/env python3
"""
Generate FmodConstants.java from FMOD header files.
This ensures constants are always in sync with the actual FMOD headers.
Run from src/main/resources/fmod/macos/ directory.
"""

import re
import os
from pathlib import Path

def parse_enum(header_content, enum_name):
    """Parse a C enum and return a dict of name -> value."""
    # Find the enum definition
    pattern = rf'typedef enum {enum_name}\s*\{{(.*?)\}}'
    match = re.search(pattern, header_content, re.DOTALL)
    if not match:
        return {}
    
    enum_body = match.group(1)
    constants = {}
    current_value = 0
    
    for line in enum_body.split('\n'):
        line = line.strip()
        if not line or line.startswith('//') or line.startswith('/*'):
            continue
            
        # Remove trailing comma and comments
        line = re.sub(r'//.*', '', line)
        line = re.sub(r'/\*.*?\*/', '', line)
        line = line.rstrip(',').strip()
        
        if not line or line == '}':
            continue
            
        # Check if value is explicitly assigned
        if '=' in line:
            parts = line.split('=')
            name = parts[0].strip()
            value_str = parts[1].strip()
            
            # Handle hex values
            if value_str.startswith('0x'):
                current_value = int(value_str, 16)
            # Handle bit shift operations
            elif '<<' in value_str:
                base, shift = value_str.split('<<')
                current_value = int(base.strip()) << int(shift.strip())
            # Handle decimal values
            else:
                try:
                    current_value = int(value_str)
                except:
                    # Skip complex expressions
                    continue
        else:
            name = line.strip()
        
        if name and not name.endswith('FORCEINT'):
            constants[name] = current_value
            current_value += 1
    
    return constants

def parse_defines(header_content, prefix):
    """Parse #define constants with a given prefix."""
    constants = {}
    pattern = rf'#define\s+({prefix}\w+)\s+(0x[0-9A-Fa-f]+|\d+)'
    
    for match in re.finditer(pattern, header_content):
        name = match.group(1)
        value_str = match.group(2)
        
        if value_str.startswith('0x'):
            value = int(value_str, 16)
        else:
            value = int(value_str)
        
        constants[name] = value
    
    return constants

def main():
    # Get paths relative to this script
    script_dir = Path(__file__).parent
    
    # Read header files in current directory
    fmod_common = (script_dir / "fmod_common.h").read_text()
    fmod_h = (script_dir / "fmod.h").read_text()
    
    # Parse all the enums and defines we need
    all_constants = {}
    
    # Parse FMOD_RESULT enum for error codes
    results = parse_enum(fmod_common, 'FMOD_RESULT')
    all_constants.update(results)
    
    # Parse other enums
    for enum_name in ['FMOD_OUTPUTTYPE', 'FMOD_SPEAKERMODE', 'FMOD_SOUND_TYPE', 
                      'FMOD_SOUND_FORMAT', 'FMOD_OPENSTATE', 'FMOD_SOUNDGROUP_BEHAVIOR',
                      'FMOD_CHANNELCONTROL_TYPE', 'FMOD_THREAD_TYPE']:
        constants = parse_enum(fmod_common, enum_name)
        all_constants.update(constants)
    
    # Parse FMOD_TIMEUNIT flags
    timeunit_pattern = r'#define\s+(FMOD_TIMEUNIT_\w+)\s+(0x[0-9A-Fa-f]+)'
    for match in re.finditer(timeunit_pattern, fmod_common):
        name = match.group(1)
        value = int(match.group(2), 16)
        all_constants[name] = value
    
    # Parse FMOD_MODE flags (from fmod_common.h)
    # These are defined after "typedef unsigned int FMOD_MODE;"
    # Find the FMOD_MODE typedef and get all defines after it until the next typedef
    mode_section_match = re.search(
        r'typedef unsigned int FMOD_MODE;(.*?)(?=typedef|/\*\s+FMOD_TIMEUNIT)',
        fmod_common, re.DOTALL)
    if mode_section_match:
        mode_section = mode_section_match.group(1)
        mode_pattern = r'#define\s+(FMOD_[A-Z0-9_]+)\s+(0x[0-9A-Fa-f]+)'
        for match in re.finditer(mode_pattern, mode_section):
            name = match.group(1)
            value = int(match.group(2), 16)
            all_constants[name] = value
    
    # Parse FMOD_INITFLAGS (from fmod_common.h, not fmod.h)
    init_pattern = r'#define\s+(FMOD_INIT_\w+)\s+(0x[0-9A-Fa-f]+)'
    for match in re.finditer(init_pattern, fmod_common):
        name = match.group(1)
        value = int(match.group(2), 16)
        all_constants[name] = value
    
    # Parse FMOD_SYSTEM_CALLBACK_TYPE flags
    callback_pattern = r'#define\s+(FMOD_SYSTEM_CALLBACK_\w+)\s+(0x[0-9A-Fa-f]+)'
    for match in re.finditer(callback_pattern, fmod_common):
        name = match.group(1)
        value = int(match.group(2), 16)
        all_constants[name] = value
    
    # Parse FMOD_CHANNELMASK flags
    channelmask_pattern = r'#define\s+(FMOD_CHANNELMASK_\w+)\s+(0x[0-9A-Fa-f]+)'
    for match in re.finditer(channelmask_pattern, fmod_common):
        name = match.group(1)
        value = int(match.group(2), 16)
        all_constants[name] = value
    
    # Add FMOD_VERSION
    version_match = re.search(r'#define\s+FMOD_VERSION\s+(0x[0-9A-Fa-f]+)', fmod_common)
    if version_match:
        all_constants['FMOD_VERSION'] = int(version_match.group(1), 16)
    
    # Group constants by category
    categories = {
        'Version': ['FMOD_VERSION'],
        'Result codes': [],
        'FMOD_INITFLAGS': [],
        'FMOD_MODE': [],
        'FMOD_TIMEUNIT': [],
        'FMOD_SYSTEM_CALLBACK_TYPE': [],
        'FMOD_CHANNELMASK': [],
        'FMOD_OUTPUTTYPE': [],
        'FMOD_SPEAKERMODE': [],
        'FMOD_SOUND_TYPE': [],
    }
    
    # Categorize constants
    for name in sorted(all_constants.keys()):
        if name == 'FMOD_VERSION':
            continue
        elif name.startswith('FMOD_OK') or name.startswith('FMOD_ERR_'):
            categories['Result codes'].append(name)
        elif name.startswith('FMOD_INIT_'):
            categories['FMOD_INITFLAGS'].append(name)
        elif name.startswith('FMOD_TIMEUNIT_'):
            categories['FMOD_TIMEUNIT'].append(name)
        elif name.startswith('FMOD_SYSTEM_CALLBACK_'):
            categories['FMOD_SYSTEM_CALLBACK_TYPE'].append(name)
        elif name.startswith('FMOD_CHANNELMASK_'):
            categories['FMOD_CHANNELMASK'].append(name)
        elif name.startswith('FMOD_OUTPUTTYPE_'):
            categories['FMOD_OUTPUTTYPE'].append(name)
        elif name.startswith('FMOD_SPEAKERMODE_'):
            categories['FMOD_SPEAKERMODE'].append(name)
        elif name.startswith('FMOD_SOUND_TYPE_'):
            categories['FMOD_SOUND_TYPE'].append(name)
        elif name.startswith('FMOD_'):
            # These are likely MODE flags
            categories['FMOD_MODE'].append(name)
    
    # Generate Java file
    java_code = """package a2.fmod;

import lombok.experimental.UtilityClass;

/**
 * FMOD Core API constants from fmod_common.h and fmod.h.
 * Auto-generated from FMOD headers - DO NOT EDIT MANUALLY.
 * Generated from FMOD version 2.03.09
 */
@UtilityClass
class FmodConstants {

"""
    
    for category, names in categories.items():
        if not names:
            continue
            
        java_code += f"    // {category}\n"
        
        for name in names:
            if name not in all_constants:
                continue
            value = all_constants[name]
            
            # Format the value
            if value >= 0x1000000:  # Large hex values
                value_str = f"0x{value:08X}"
            elif value >= 0x100:  # Medium hex values
                value_str = f"0x{value:08X}"
            elif category in ['FMOD_MODE', 'FMOD_INITFLAGS', 'FMOD_TIMEUNIT', 
                             'FMOD_SYSTEM_CALLBACK_TYPE', 'FMOD_CHANNELMASK']:
                # These are typically hex flags
                value_str = f"0x{value:08X}"
            else:
                # Regular decimal
                value_str = str(value)
            
            # Special case for FMOD_VERSION - add comment
            if name == 'FMOD_VERSION':
                java_code += f"    static final int {name} = {value_str}; // 2.03.09\n"
            else:
                java_code += f"    static final int {name} = {value_str};\n"
        
        java_code += "\n"
    
    java_code += "}\n"
    
    # Write the Java file using relative path from this directory
    # Navigate up to project root then to Java source
    output_path = script_dir / "../../../java/a2/fmod/FmodConstants.java"
    output_path = output_path.resolve()
    
    output_path.write_text(java_code)
    print(f"Generated {output_path}")
    print(f"Total constants: {len(all_constants)}")

if __name__ == "__main__":
    main()