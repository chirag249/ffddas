import os
import re

# Base directory
base_dir = r'C:\Users\chira\AndroidStudioProjects\ffddas\OpenCV\native\jni'

# Directories to process
abi_dirs = ['abi-arm64-v8a', 'abi-armeabi-v7a', 'abi-x86', 'abi-x86_64']

# Files to process in each directory
files_to_fix = ['OpenCVConfig.cmake', 'OpenCVModules-release.cmake']

for abi_dir in abi_dirs:
    abi_path = os.path.join(base_dir, abi_dir)
    if not os.path.exists(abi_path):
        print(f"Skipping {abi_dir} - directory not found")
        continue
    
    for filename in files_to_fix:
        filepath = os.path.join(abi_path, filename)
        if not os.path.exists(filepath):
            print(f"Skipping {filepath} - file not found")
            continue
        
        print(f"Processing {filepath}")
        
        # Read the file
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Replace /sdk/native/ with /native/
        new_content = content.replace('/sdk/native/', '/native/')
        
        # Write back
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        
        print(f"Fixed {filepath}")

print("Done!")
