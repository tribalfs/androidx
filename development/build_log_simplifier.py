#!/usr/bin/python3
#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
import sys

def usage():
    print("""USAGE:
    Simplifies a build.log from hundreds of megabytes to <100 lines. Prints output to terminal.
    Pass this script a filepath to parse. You should be able to type "python3 build_log_simplifier.py"
    And then drag-and-drop a log file onto the terminal window to get its path.

    Sample usage: python3 development/build_log_simplifier.py Users/owengray/Desktop/build.log
    """)
    exit(1)

def select_failing_task_output(lines):
    tasks_of_interest = []
    # first, find tasks of interest
    for line in lines:
        if line.startswith("Execution failed for task"):
            tasks_of_interest.append(line.split("task '")[1][:-3])


    print("Detected these failing tasks: " + str(tasks_of_interest))

    # next, save all excerpts between start(interesting task) and end(interesting task)
    current_interesting_tasks = []
    retained_lines = []
    for line in lines:
        if line.startswith("Task ") and line.split(" ")[1] in tasks_of_interest:
            if line.split(" ")[-1].strip() == "Starting":
                current_interesting_tasks.append(line.split(" ")[1])
            elif line.split(" ")[-1].strip() == "Finished":
                current_interesting_tasks.remove(line.split(" ")[1])
                retained_lines.append(line)
        if current_interesting_tasks: retained_lines.append(line)
    if retained_lines:
        return retained_lines
    # if no output was created by any failing tasks, then maybe there could be useful output from
    # somewhere else
    return lines

try:
    build_log_loc = sys.argv[1]
    
    infile = open(build_log_loc)
    lines = infile.readlines()
    infile.close()
    
    retained_lines = select_failing_task_output(lines)
    
    print(len(retained_lines))
    print(''.join(retained_lines))
except Exception as e:
    print("An error occurred! "+str(e))
    usage()
