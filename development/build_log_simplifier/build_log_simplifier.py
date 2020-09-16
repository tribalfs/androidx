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
import argparse, collections, pathlib, os, re, sys

dir_of_this_script = str(pathlib.Path(__file__).parent.absolute())

parser = argparse.ArgumentParser(
    description="""USAGE:
    Simplifies a build.log from hundreds of megabytes to <100 lines. Prints output to terminal.
    Pass this script a filepath to parse. You should be able to type "python3 build_log_simplifier.py"
    And then drag-and-drop a log file onto the terminal window to get its path.

    Sample usage: python3 development/build_log_simplifier.py Users/owengray/Desktop/build.log
    """)
parser.add_argument("--validate", action="store_true", help="Validate that no unrecognized messages exist in the given log")
parser.add_argument("--update", action="store_true", help="Update our list of recognized messages to include all messages from the given log")
parser.add_argument("log_path", help="Filepath of log to process", nargs=1)

# a regexes_matcher can quickly identify which of a set of regexes matches a given text
class regexes_matcher(object):
    def __init__(self, regexes):
        self.regex_texts = regexes
        self.children = None
        self.matcher = None

    # returns a list of regexes that match the given text
    def get_matching_regexes(self, text, expect_match=True):
        if expect_match and len(self.regex_texts) > 1:
            # If we already expect our matcher to match, we can directly jump to asking our children
            return self.query_children_for_matching_regexes(text)
        # It takes more time to match lots of regexes than to match one composite regex
        # So, we try to match one composite regex first
        if self.matches(text):
            if len(self.regex_texts) > 1:
                # At least one child regex matches, so we have to determine which ones
                return self.query_children_for_matching_regexes(text)
            else:
                return self.regex_texts
        # Our composite regex yielded no matches
        return []

    # queries our children for regexes that match <text>
    def query_children_for_matching_regexes(self, text):
        # Create children if they don't yet exist
        self.ensure_split()
        # query children and join their results
        results = []
        for child in self.children:
            results += child.get_matching_regexes(text, False)
        return results

    # Returns the index of the first regex matching this string, or None of not found
    def index_first_matching_regex(self, text):
        if len(self.regex_texts) <= 1:
            if len(self.regex_texts) == 0:
                return None
            if self.matches(text):
                return 0
            return None
        self.ensure_split()
        count = 0
        for child in self.children:
            child_index = child.index_first_matching_regex(text)
            if child_index is not None:
                return count + child_index
            count += len(child.regex_texts)
        return None

    # Create children if they don't yet exist
    def ensure_split(self):
        if self.children is None:
            # It takes more time to compile a longer regex, but it also takes more time to
            # test lots of small regexes.
            # In practice, this number of children seems to result in fast execution
            num_children = min(len(self.regex_texts), 32)
            child_start = 0
            self.children = []
            for i in range(num_children):
                child_end = int(len(self.regex_texts) * (i + 1) / num_children)
                self.children.append(regexes_matcher(self.regex_texts[child_start:child_end]))
                child_start = child_end


    def matches(self, text):
        if self.matcher is None:
            full_regex_text = "(?:" + ")|(?:".join(self.regex_texts) + ")"
            self.matcher = re.compile(full_regex_text)
        return self.matcher.fullmatch(text)


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

def shorten_uninteresting_stack_frames(lines):
    result = []
    prev_line_is_boring = False
    for line in lines:
        if line.startswith("\tat org.gradle"):
            if not prev_line_is_boring:
                result.append("\tat org.gradle...\n")
            prev_line_is_boring = True
        elif line.startswith("\tat java.base"):
            if not prev_line_is_boring:
                result.append("\tat java.base...")
            prev_line_is_boring = True
        else:
            result.append(line)
            prev_line_is_boring = False
    return result

def remove_known_uninteresting_lines(lines):
  skipLines = {
      "A fine-grained performance profile is available: use the --scan option.",
      "* Get more help at https://help.gradle.org",
      "Use '--warning-mode all' to show the individual deprecation warnings.",
      "See https://docs.gradle.org/6.5/userguide/command_line_interface.html#sec:command_line_warnings",

      "Note: Some input files use or override a deprecated API.",
      "Note: Recompile with -Xlint:deprecation for details.",
      "Note: Some input files use unchecked or unsafe operations.",
      "Note: Recompile with -Xlint:unchecked for details.",

      "w: ATTENTION!",
      "This build uses unsafe internal compiler arguments:",
      "-XXLanguage:-NewInference",
      "-XXLanguage:+InlineClasses",
      "This mode is not recommended for production use,",
      "as no stability/compatibility guarantees are given on",
      "compiler or generated code. Use it at your own risk!"
  }
  skipPrefixes = [
      "See the profiling report at:",

      "Deprecated Gradle features were used in this build"
  ]
  result = []
  for line in lines:
      stripped = line.strip()
      if stripped in skipLines:
          continue
      include = True
      for prefix in skipPrefixes:
          if stripped.startswith(prefix):
              include = False
              break
      if include:
          result.append(line)
  return result

def get_exemptions_path():
    return os.path.join(dir_of_this_script, "build_log_simplifier/messages.ignore")


# Returns a regexes_matcher that matches what is described by our config file
# Ignores comments and ordering in our config file
def build_exemptions_matcher(config_lines):
    config_lines = [line.replace("\n", "") for line in config_lines]
    regexes = []
    for line in config_lines:
        line = line.strip()
        if line.startswith("#") or line == "":
            # skip comments
            continue
        regexes.append(line)
        if remove_control_characters(line) != line:
            raise Exception("Unexpected control characters found in configuration line:\n\n " +
                "'" + line + "'\n\n. This line is unexpected to match anything. Is this a copying mistake?")

    return regexes_matcher(sorted(regexes))

# Returns a regexes_matcher that matches the content of our config file
# Can match comments
# Respects ordering in the config
# This is used for editing the config file itself
def build_exemptions_code_matcher(config_lines):
    config_lines = [line.strip() for line in config_lines]
    regexes = []
    for line in config_lines:
        line = line.strip()
        if line == "":
            continue
        regexes.append(line)
    return regexes_matcher(regexes)

def remove_configured_uninteresting_lines(lines, config_lines, validate_no_duplicates):
    fast_matcher = build_exemptions_matcher(config_lines)
    result = []
    for line in lines:
        stripped = line.strip()
        matching_exemptions = fast_matcher.get_matching_regexes(stripped, expect_match=True)
        if validate_no_duplicates and len(matching_exemptions) > 1:
           print("")
           print("build_log_simplifier.py: Invalid configuration: multiple message exemptions match the same message. Are some exemptions too broad?")
           print("")
           print("Line: '" + stripped + "'")
           print("")
           print(str(len(matching_exemptions)) + " Matching exemptions:")
           for exemption_text in matching_exemptions:
               print("'" + exemption_text + "'")
           exit(1)
        if len(matching_exemptions) < 1:
            result.append(line)
    return result

def collapse_consecutive_blank_lines(lines):
    result = []
    prev_blank = True
    for line in lines:
        if line.strip() == "":
            if not prev_blank:
                result.append(line)
            prev_blank = True
        else:
            result.append(line)
            prev_blank = False
    return result

# If a task has no output (or only blank output), this function removes the task (and its output)
# For example, turns this:
#  > Task :a
#  > Task :b
#  some message
#
# into this:
#
#  > Task :b
#  some message
def collapse_tasks_having_no_output(lines):
    result = []
    # When we see a task name, we might not emit it if it doesn't have any output
    # This variable is that pending task name, or none if we have no pending task
    pending_task = None
    pending_blanks = []
    for line in lines:
        is_task = line.startswith("> Task ")
        if is_task:
            pending_task = line
            pending_blanks = []
        elif line.strip() == "":
            # If we have a pending task and we found a blank line, then hold the blank line,
            # and only output it if we later find some nonempty output
            if pending_task is not None:
                pending_blanks.append(line)
            else:
                result.append(line)
        else:
            # We found some nonempty output, now we emit any pending task names
            if pending_task is not None:
                result.append(pending_task)
                result += pending_blanks
                pending_task = None
                pending_blanks = []
            result.append(line)
    return result

# Removes color characters and other ANSI control characters from this input
control_character_regex = re.compile(r"""
        \x1B  # Escape
        (?:   # 7-bit C1 Fe (except CSI)
            [@-Z\\-_]
        |     # or [ for CSI, followed by a control sequence
            \[
            [0-?]*  # Parameters
            [ -/]*  # Intermediate bytes
            [@-~]   # End
        )
        """, re.VERBOSE)

def remove_control_characters(line):
    return control_character_regex.sub("", line)

# Normalizes some filepaths to more easily simplify/skip some messages
def normalize_paths(lines):
    # get OUT_DIR, DIST_DIR, and the path of the root of the checkout
    out_dir = None
    dist_dir = None
    checkout_dir = None
    # we read checkout_root from the log file in case this build was run in a location,
    # such as on a build server
    out_marker = "OUT_DIR="
    dist_marker = "DIST_DIR="
    checkout_marker = "CHECKOUT="
    for line in lines:
        if line.startswith(out_marker):
            out_dir = line.split(out_marker)[1].strip()
            continue
        if line.startswith(dist_marker):
            dist_dir = line.split(dist_marker)[1].strip()
            continue
        if line.startswith(checkout_marker):
            checkout_dir = line.split(checkout_marker)[1].strip()
            continue
        if out_dir is not None and dist_dir is not None and checkout_dir is not None:
            break

    # Remove any mentions of these paths, and replace them with consistent values
    remove_paths = collections.OrderedDict()
    if dist_dir is not None:
        remove_paths[dist_dir] = "$DIST_DIR"
    if out_dir is not None:
        remove_paths[out_dir] = "$OUT_DIR"
    if checkout_dir is not None:
        remove_paths[checkout_dir + "/frameworks/support"] = "$SUPPORT"
        remove_paths[checkout_dir] = "$CHECKOUT"
    result = []
    for line in lines:
        for path in remove_paths:
            if path in line:
                replacement = remove_paths[path]
                line = line.replace(path + "/", replacement + "/")
                line = line.replace(path, replacement)
        result.append(line)
    return result

# Given a regex with hashes in it like ".gradle/caches/transforms-2/files-2.1/73f631f487bd87cfd8cb2aabafbac6a8",
# tries to return a more generalized regex like ".gradle/caches/transforms-2/files-2.1/[0-9a-f]{32}"
def generalize_hashes(message):
    hash_matcher = "[0-9a-f]{32}"
    return re.sub(hash_matcher, hash_matcher, message)

# Given a regex with numbers in it like ".gradle/caches/transforms-2/files-2.1/73f631f487bd87cfd8cb2aabafbac6a8"
# tries to return a more generalized regex like ".gradle/caches/transforms-[0-9]*/files-[0-9]*.[0-9]*/73f631f487bd87cfd8cb2aabafbac6a8"
def generalize_numbers(message):
    matcher = "[0-9]+"
    generalized = re.sub(matcher, matcher, message)
    # the above replacement corrupts strings of the form "[0-9a-f]{32}", so we fix them before returning
    return generalized.replace("[[0-9]+-[0-9]+a-f]{[0-9]+}", "[0-9a-f]{32}")

# Given a list of output messages and a list of existing exemption lines,
# generates an augmented list of exemption lines and writes that to <dest_path>
def generate_suggested_exemptions(messages, config_lines):
    # given a message, finds the index of the existing exemption for that message, if any
    existing_matcher = build_exemptions_code_matcher(config_lines)
    # the index of the previously matched exemption
    previous_found_index = -1
    # map from line index to list of lines to insert there
    insertions_by_position = collections.defaultdict(lambda: [])
    insertions_by_task_name = collections.OrderedDict()
    # current task generating any subsequent output
    pending_task_line = None
    # new, suggested exemptions
    new_suggestions = set()
    # generate new suggestions
    for line in messages:
        line = line.strip()
        if line == "":
            continue
        # save task name
        is_task = False
        if line.startswith("> Task :"):
            # If a task creates output, we record its name
            line = "# " + line
            pending_task_line = line
            is_task = True
        # determine where to put task name
        current_found_index = existing_matcher.index_first_matching_regex(line)
        if current_found_index is not None:
            # We already have a mention of this line
            # We don't need to exempt it again, but this informs where to insert our next exemption
            previous_found_index = current_found_index
            pending_task_line = None
            continue
        # skip outputting task names for tasks that don't output anything
        if is_task:
            continue

        # escape message
        escaped = re.escape(line)
        escaped = escaped.replace("\ ", " ") # spaces don't need to be escaped
        escaped = generalize_hashes(escaped)
        escaped = generalize_numbers(escaped)
        # confirm that we haven't already inserted this message
        if escaped in new_suggestions:
            continue
        # insert this regex into an appropriate position
        if pending_task_line is not None:
            # We know which task this line came from, and it's a task that didn't previously make output
            if pending_task_line not in insertions_by_task_name:
                insertions_by_task_name[pending_task_line] = []
            insertions_by_task_name[pending_task_line].append(escaped)
        else:
            # This line of output didn't come from a new task
            # So we append it after the previous line that we found
            insertions_by_position[previous_found_index].append(escaped)
        new_suggestions.add(escaped)

    # for each regex for which we chose a position in the file, insert it there
    exemption_lines = []
    for i in range(len(existing_matcher.regex_texts)):
        exemption_lines.append(existing_matcher.regex_texts[i])
        if i in insertions_by_position:
            exemption_lines += insertions_by_position[i]
    # for regexes that could not be assigned to a task, insert them next
    if -1 in insertions_by_position:
        exemption_lines += insertions_by_position[-1]
    # for regexes that were simply assigned to certain task names, insert the there, grouped by task
    for task_name in insertions_by_task_name:
        exemption_lines.append(task_name)
        exemption_lines += insertions_by_task_name[task_name]
    return exemption_lines

# opens a file and reads the lines in it
def readlines(path):
    infile = open(path)
    lines = infile.readlines()
    infile.close()
    return lines

def writelines(path, lines):
    destfile = open(path, 'w')
    destfile.write("\n".join(lines))
    destfile.close()

def main():
    arguments = parser.parse_args()

    # read file
    log_path = arguments.log_path[0]
    lines = readlines(log_path)
    lines = [remove_control_characters(line) for line in lines]
    lines = normalize_paths(lines)
    # load configuration
    exemption_regexes_from_file = readlines(get_exemptions_path())
    # remove lines we're not interested in
    if not arguments.validate:
        lines = select_failing_task_output(lines)
    lines = shorten_uninteresting_stack_frames(lines)
    lines = remove_known_uninteresting_lines(lines)
    lines = remove_configured_uninteresting_lines(lines, exemption_regexes_from_file, arguments.validate)
    lines = collapse_tasks_having_no_output(lines)
    lines = collapse_consecutive_blank_lines(lines)

    # process results
    if arguments.validate:
        if len(lines) != 0:
            print("")
            print("build_log_simplifier.py: Error: Found new messages!")
            print("")
            print("".join(lines))
            print("Error: build_log_simplifier.py found " + str(len(lines)) + " new messages found in " + log_path + ".")
            new_exemptions_path = log_path + ".ignore"
            suggested = generate_suggested_exemptions(lines, exemption_regexes_from_file)
            writelines(new_exemptions_path, suggested)
            print("")
            print("Please fix or suppress these new messages in the tool that generates them.")
            print("If you cannot, then you can exempt them by doing:")
            print("")
            print("  1. cp " + new_exemptions_path + " " + get_exemptions_path())
            print("  2. modify the new lines to be appropriately generalized")
            print("")
            print("Note that if you exempt these messages by updating the exemption file, it will only take affect for CI builds and not for Android Studio.")
            print("Additionally, adding more exemptions to this exemption file runs more slowly than fixing or suppressing the message where it is generated.")
            exit(1)
    elif arguments.update:
        if len(lines) != 0:
            update_path = get_exemptions_path()
            suggested = generate_suggested_exemptions(lines, configuration_lines)
            writelines(update_path, suggested)
            print("build_log_simplifier.py updated exemptions " + update_path)
    else:
        print("".join(lines))

if __name__ == "__main__":
    main()
