# Legal and Licensing Analysis for an Apache-2.0 Java Test Quality CLI Tool

Software architecture decisions in open-source projects frequently
intersect with complex legal frameworks, software license terms,
foundation distribution policies, and trademark protections. When
building a Command Line Interface (CLI) tool licensed under the Apache
License, Version 2.0 (Apache-2.0) that integrates with third-party Java
testing tools, developers must carefully evaluate the legal implications
of dynamic classloading, binary bundling, inter-process communication,
dual-licensing mechanisms, and brand usage guidelines.

This report provides an exhaustive analysis of the licensing and
trademark questions associated with integrating JaCoCo, Descartes
(pitest-descartes), and JavaParser into an Apache-2.0 CLI application,
alongside compliance constraints imposed by brand owners such as
SonarSource SA and the Eclipse Foundation.

## 1. JaCoCo Integration and EPL-2.0 Compliance under ASF Policy

JaCoCo is an open-source Java code coverage technology distributed under
the Eclipse Public License 2.0 (EPL-2.0)^1^. Evaluating JaCoCo
integration requires distinguishing between in-process class linkage via
build dependencies and the physical bundling of agent binaries within
distribution archives.

### In-Process Execution via Maven Dependencies

Incorporating org.jacoco.core and org.jacoco.report as ordinary Maven
dependencies to parse .exec binary files in-process creates a dynamic
execution linkage^2^. Under Section 1 of EPL-2.0, \"Modified Works\"
explicitly excludes separate modules that merely link to, bind by name,
or subclass the covered program^3^. Because the CLI tool links to
unmodified JaCoCo binaries via standard Java Application Programming
Interfaces (APIs), the CLI codebase remains a distinct, independent
work^3^. Consequently, adopting an Apache-2.0 license for the primary
CLI source repository is legally valid and does not trigger copyleft
contamination of the primary codebase^2^.

### Apache Software Foundation Third-Party License Policy (Category B)

The Apache Software Foundation (ASF) Third-Party License Policy
classifies EPL-2.0 as a **Category B** (\"Weak Copyleft\") license^2^.
Category B licenses impose reciprocal obligations on modified versions
of the covered library itself but permit aggregation and linking with
permissive software under defined conditions^2^. The policy dictates two
primary distribution rules:

-   **Source Release Exclusion**: Category B works must **not** be
    > included in source code releases^2^. The source repository of an
    > Apache-2.0 project must reference third-party Category B
    > dependencies strictly via dependency descriptors (such as Maven
    > pom.xml files) rather than committing Category B source files or
    > binary JARs directly into the source control tree^2^.

-   **Binary-Only Inclusion Permission**: Category B works may be
    > bundled in **binary-only form** within convenience binary
    > distributions, such as pre-packaged release archives containing
    > compiled execution JARs^2^.

### Binary Redistribution of jacocoagent.jar

Bundling jacocoagent.jar inside the CLI tool\'s distribution ZIP archive
is fully compatible with maintaining an Apache-2.0 license for the
overall distribution, provided the release complies with both EPL-2.0
distribution requirements and the ASF Category B \"Appropriately
Labelled\" condition^1^. The distributor must satisfy four mandatory
obligations:

-   **Appropriately Labelled Condition**: Project distribution metadata
    > and user documentation (such as a root README file) must
    > prominently inform downstream users of the inclusion of JaCoCo,
    > identify EPL-2.0 as its governing license, and provide a uniform
    > resource locator (URL) pointing to the official JaCoCo
    > homepage^2^.

-   **Retention of Copyright and Legal Notices**: Pursuant to EPL-2.0
    > Section 3.3, all copyright, patent, trademark, and attribution
    > notices contained within the original JaCoCo library must be
    > retained without modification^4^.

-   **Source Code Availability Statement**: EPL-2.0 Section 3.1(a)
    > dictates that binary distribution of EPL-2.0 software must be
    > accompanied by a statement informing recipients how to obtain the
    > source code of the EPL-2.0 work in a reasonable manner on a medium
    > customarily used for software exchange^1^.

-   **NOTICE File Entry**: Specific third-party attribution and
    > copyright statements must be included in the project\'s NOTICE
    > file^2^.

EPL-2.0 includes an optional Exhibit A mechanism for initial
contributors to declare Secondary Licenses, specifically the GNU General
Public License version 2.0 or later (GPLv2+)^1^. JaCoCo is distributed
under standard EPL-2.0 without Secondary License designations^1^. As a
result, downstream distributors consume JaCoCo solely under EPL-2.0
rules, avoiding secondary GPL copyleft considerations^1^.

  -------------------------------------------------------------------------------------
  **Distribution Channel  **Source       **Binary       **Governing    **Mandatory
  / Artifact**            Release        Release (.zip  License & ASF  Compliance
                          (.tar.gz /     Archive)**     Category**     Action**
                          Git)**                                       
  ----------------------- -------------- -------------- -------------- ----------------
  **org.jacoco.core**     Maven POM      Embedded JAR   EPL-2.0        Label in README;
                          Reference Only Permitted      (Category B)   append copyright
                                                                       to NOTICE^2^.

  **org.jacoco.report**   Maven POM      Embedded JAR   EPL-2.0        Provide link to
                          Reference Only Permitted      (Category B)   JaCoCo project
                                                                       repository^2^.

  **jacocoagent.jar**     Excluded from  Embedded       EPL-2.0        Include EPL-2.0
                          Repository     Binary         (Category B)   source code
                                         Permitted                     availability
                                                                       notice^1^.

  **CLI Tool Core Code**  Full Source    Compiled       Apache-2.0     Distribute under
                          Included       Binary         (Category A)   standard
                                         Included                      Apache-2.0
                                                                       terms^2^.
  -------------------------------------------------------------------------------------

## 2. Descartes Integration, Process Boundaries, and LGPL-3.0 Mechanics

Descartes (pitest-descartes) is a mutation testing engine for Java
licensed under the GNU Lesser General Public License v3.0 (LGPL-3.0).
Evaluating its integration requires analyzing operating system process
boundaries, dynamic linking, and Maven dependency scopes.

### Process Isolation and Inter-Process Boundaries

When the CLI tool invokes pitest with the Descartes engine as a separate
operating system process (using runtime process execution mechanisms
such as ProcessBuilder or exec), the interaction occurs across a formal
process boundary^5^. Communication is restricted to command-line
argument passing, standard input/output streams, and reading output
report files generated on disk^5^.

Under established open-source legal principles and Free Software
Foundation (FSF) licensing interpretations, executing an independent
binary as a separate process via standard operating system system calls
does not combine the two programs into a single derivative work^5^. The
CLI tool acts as an invocation wrapper and consumer of data formats
rather than a compiled extension^5^. Consequently, no copyleft
obligations under LGPL-3.0 attach to the CLI tool\'s source code, and
the tool remains entirely governed by Apache-2.0^5^.

### Maven Dependency Scopes and ASF Category X Policy

Legal and policy complications arise if pitest-descartes is declared
directly as a dependency in the project\'s build system:

-   **Compile or Runtime Scope**: Declaring pitest-descartes as a
    > standard compile or runtime dependency introduces dynamic linking
    > within the same Java Virtual Machine (JVM) memory space. LGPL-3.0
    > Section 4 requires that combined works permit debugging
    > modifications and reverse engineering of the LGPL component.

-   **ASF Category X Classification**: The ASF Third-Party License
    > Policy strictly classifies LGPL-2.0, LGPL-2.1, and LGPL-3.0 under
    > **Category X** (Forbidden Licenses)^2^. ASF policy prohibits
    > bundling or redistributing Category X dependencies in ASF releases
    > in both source and binary forms^2^.

-   **Optional or Test Scope**: Declaring pitest-descartes as an
    > \<optional\>true\</optional\> or \<scope\>test\</scope\>
    > dependency in pom.xml leaves the library unbundled in production
    > distribution archives. A build file reference to an optional
    > external dependency does not contaminate the CLI source code with
    > LGPL obligations^2^. However, packaging pre-built binary releases
    > that bundle pitest-descartes.jar violates Category X rules and
    > triggers LGPL compliance requirements for the binary
    > distribution^2^.

To preserve Apache-2.0 distribution integrity, the CLI application
should avoid embedding or packaging pitest-descartes binaries^2^. The
tool must rely on out-of-process execution, requiring end-users to
provide their own local installation of the mutation testing engine^2^.

  ---------------------------------------------------------------------------------
  **Architectural   **Execution    **Copyleft      **ASF Policy    **Operational
  Integration       Space**        Implication for Status**        Requirement**
  Mode**                           CLI**                           
  ----------------- -------------- --------------- --------------- ----------------
  **External        Isolated OS    Zero Copyleft   Fully           Require
  Process           Subprocess     Attachment^5^   Compliant^2^    user-installed
  Execution**                                                      binary; invoke
                                                                   via execution
                                                                   flags^2^.

  **Compile /       Shared JVM     LGPL-3.0        Non-Compliant   Strictly
  Runtime Maven     Memory Space   Linking Terms   (Category X)^2^ prohibited from
  Scope**                          Apply                           binary
                                                                   releases^2^.

  **Optional / Test Unbundled at   No Copyleft     Compliant if    Exclude LGPL
  Maven Scope**     Runtime        Attachment to   Unbundled^2^    artifacts from
                                   Source                          distribution
                                                                   packages^2^.
  ---------------------------------------------------------------------------------

## 3. JavaParser Dual-Licensing Mechanism and Apache-2.0 Election

JavaParser is distributed under a dual-licensing framework, offering
users a choice between the GNU Lesser General Public License (LGPL) or
the Apache License, Version 2.0 (Apache-2.0)^7^.

### Dual-Licensing Legal Mechanics

A dual-license model provides software consumers with a choice between
two independent license agreements^7^. The licensor grants permissions
under both frameworks disjunctively, permitting the licensee to choose
the license terms that best fit their distribution model^7^.
JavaParser\'s distribution header explicitly outlines this choice:

> \"JavaParser is available either under the terms of the LGPL 3 License
> or the Apache 2.0 License. You as the user are entitled to choose the
> terms under which to adopt JavaParser.\"^9^

### Exercising License Election

The developers of the CLI tool can formally elect the **Apache License,
Version 2.0**^7^. Electing Apache-2.0 eliminates all copyleft
obligations, linking restrictions, and reverse-engineering terms
associated with the alternative LGPL option^7^.

To document this election properly:

1.  Include an explicit statement in the project\'s NOTICE file
    > indicating that JavaParser is included under the Apache-2.0
    > license pursuant to its dual-licensing option^7^.

2.  Retain the original JavaParser copyright notices and attach the
    > standard Apache-2.0 boilerplate attribution block^8^.

## 4. Trademark Compliance and Brand Usage Guidelines

Trademark protection is legally distinct from copyright licensing. While
open-source licenses grant broad permissions to copy, modify, and
redistribute source code, they generally do not grant rights to use
registered trademarks, trade names, or brand logos without
permission^4^.

### Nominative Fair Use in Technical Documentation

Referencing third-party mark names in documentation---such as \"reads
JaCoCo XML reports\" or \"Sonar-compatible coverage math\"---is
permissible under trademark principles of nominative fair use^12^.
Nominative fair use permits using a trademarked term strictly to refer
to the trademark owner\'s product or to indicate technical
compatibility, provided the usage satisfies three conditions:

-   The target product cannot be identified without using the
    > trademarked name^12^.

-   The mark is used only to the extent reasonably necessary to
    > establish technical context^12^.

-   The usage does not suggest sponsorship, endorsement, or affiliation
    > by the trademark owner^12^.

### SonarSource Trademark Policy Constraints

SonarSource SA maintains registered trademarks for brand identifiers
including Sonar™, SonarQube™, SonarSource™, SonarQube Server™, SonarQube
Cloud™, and SonarQube for IDE™ (collectively \"Sonar Marks\")^12^.
SonarSource enforces specific rules regarding third-party usage^12^:

-   **Adjectival Modifier Constraint**: Sonar Marks must be used
    > strictly as adjectival modifiers accompanying generic descriptive
    > nouns (e.g., \"calculates metrics compatible with SonarQube™
    > platforms\")^12^. Marks must never be used as standalone nouns or
    > verbs^12^.

-   **Prohibition in Brand Identifiers**: Sonar Marks must **not** be
    > incorporated into third-party product names, software suite names,
    > company names, domain names, GitHub repository handles, or social
    > media accounts^12^.

-   **No Implied Affiliation**: Usage must not create consumer confusion
    > regarding whether the software is developed, maintained, or
    > endorsed by SonarSource SA^12^.

-   **Trademark Attribution Requirement**: Any documentation referencing
    > Sonar Marks must include a legal attribution statement identifying
    > SonarSource SA as the trademark owner^12^.

### Trademark Risks in CLI Command Design (\--mode=sonar)

Naming a primary CLI subcommand, execution flag, or mode parameter
strictly as sonar (e.g., mytool \--mode=sonar or mytool sonar)
introduces trademark risk^12^:

-   **Noun Usage**: Defining sonar as a standalone parameter option uses
    > the trademark as a functional feature noun rather than an
    > adjectival modifier, violating trademark usage rules^12^.

-   **Implied Association**: End-users may interpret a command like
    > mytool sonar as an official plugin, sub-agent, or module authored
    > by SonarSource^12^.

### Recommended Interface Naming Mitigation

To ensure full compliance with SonarSource trademark policies while
providing clear user instructions, CLI parameters should adopt generic,
descriptive schema terms^12^.

-   **Avoid**: mytool \--mode=sonar or mytool sonar^12^.

-   **Adopt**: mytool \--format=sonar-xml, mytool
    > \--profile=sonarqube-compatible, or mytool export
    > \--schema=sonarqube^12^.

  ------------------------------------------------------------------------------------------------------------------------
  **Brand /     **Usage         **Proposed Implementation** **Risk      **Policy           **Remediation / Compliance
  Trademark     Location**                                  Level**     Assessment**       Requirement**
  Context**                                                                                
  ------------- --------------- --------------------------- ----------- ------------------ -------------------------------
  **JaCoCo**    User            \"reads JaCoCo XML          Low Risk    Permissible        Retain clear, factual
                Documentation   reports\"                               nominative fair    context^12^.
                                                                        use^12^.           

  **Sonar /     User            \"Sonar-compatible coverage Moderate    Permissible with   Add superscript ™ and owner
  SonarQube**   Documentation   math\"                      Risk        proper adjectival  attribution notice^12^.
                                                                        syntax and         
                                                                        attribution^12^.   

  **Sonar**     CLI Subcommand  mytool sonar analyze        High Risk   Violates policy    Change command to mytool export
                                                                        prohibiting        \--target=sonarqube.
                                                                        standalone mark    
                                                                        usage as a product 
                                                                        noun^12^.          

  **Sonar**     CLI Flag Option mytool \--mode=sonar        Moderate    Uses mark as a     Change flag to
                                                            Risk        feature identifier \--format=sonar-xml.
                                                                        rather than an     
                                                                        adjective^12^.     

  **Sonar**     GitHub          github.com/user/sonar-cli   Critical    Direct             Rename repository to
                Repository                                  Risk        infringement by    github.com/user/coverage-cli.
                                                                        embedding mark in  
                                                                        repository         
                                                                        name^12^.          
  ------------------------------------------------------------------------------------------------------------------------

## 5. NOTICE File Checklist and Prohibited Wording

This section outlines the requirements for constructing a project NOTICE
file under Apache-2.0 and details specific phrasing to avoid in public
documentation and codebases.

### Concrete NOTICE File Checklist

An Apache-2.0 compliant NOTICE file must provide clear, structured
third-party attributions^2^. The file should include:

-   \[ \] **Primary Copyright Declaration**: State the project name,
    > copyright year, and primary copyright holder under the Apache
    > License 2.0^8^.

-   \[ \] **JavaParser Attribution Section**:

    -   Identify the JavaParser component^7^.

    -   Explicitly state the license election: *\"Licensed under the
        > Apache License, Version 2.0 pursuant to the author\'s
        > dual-license option.\"*\
        > \[cite: 7, 8, 10\]

    -   Retain the original JavaParser copyright statements^9^.

-   \[ \] **JaCoCo (EPL-2.0) Attribution Section**:

    -   List bundled JaCoCo binary artifacts (org.jacoco.core,
        > org.jacoco.report, jacocoagent.jar)^1^.

    -   Include the JaCoCo copyright statement: *\"Copyright (c) 2009,
        > 2024 Mountainminds GmbH & Co. KG and Contributors.\"*\
        > \[cite: 1, 4\]

    -   State governance under Eclipse Public License 2.0 (EPL-2.0)^1^.

    -   Provide the JaCoCo source code availability notice: *\"Source
        > code for JaCoCo is available at
        > https://github.com/jacoco/jacoco.\"*\
        > \[cite: 1, 4\]

-   \[ \] **Descartes Subprocess Disclaimer**:

    -   Clarify that Descartes is an external tool executed as a
        > separate process and is not bundled within the distribution
        > package^2^.

-   \[ \] **Trademark Disclaimer Block**:

    -   Include third-party trademark ownership statements^12^:
        > *\"JaCoCo is a trademark of the Eclipse Foundation. Sonar,
        > SonarQube, and SonarSource are registered trademarks of
        > SonarSource SA. This software is an independent implementation
        > and is not endorsed, sponsored, or affiliated with SonarSource
        > SA or the Eclipse Foundation.\"*^12^

#### Sample NOTICE File Implementation

> Java Test Quality Analysis CLI\
> Copyright 2026 \[Project Authors / Organization\]\
> \
> This product includes software developed under the Apache License
> 2.0.\
> \
> ========================================================================\
> Third-Party Software Attributions\
> ========================================================================\
> \
> JavaParser\
> Copyright (C) The JavaParser Team\
> JavaParser is dual-licensed under LGPL-3.0 and the Apache License
> 2.0.\
> This project elects to receive JavaParser under the terms of the\
> Apache License 2.0.\
> \
> JaCoCo (Java Code Coverage Library)\
> Copyright (c) 2009, 2024 Mountainminds GmbH & Co. KG and Contributors\
> This distribution bundles unmodified binary artifacts of JaCoCo\
> (org.jacoco.core, org.jacoco.report, jacocoagent.jar) under the\
> Eclipse Public License 2.0 (EPL-2.0).\
> Source code for JaCoCo is available at https://www.jacoco.org/jacoco.\
> \
> Descartes Mutation Engine\
> Descartes is licensed under LGPL-3.0. It is not bundled or
> distributed\
> within this product archive. This tool provides invocation support
> for\
> external, user-installed Descartes processes.\
> \
> ========================================================================\
> Trademark Notices\
> ========================================================================\
> \
> JaCoCo is a trademark of the Eclipse Foundation.\
> Sonar, SonarQube, and SonarSource are trademarks or registered
> trademarks\
> of SonarSource SA.\
> This project is an independent open-source tool and is not affiliated\
> with, endorsed by, or sponsored by SonarSource SA or the Eclipse
> Foundation.

### Prohibited Wording and Parameter Identifiers

To prevent trademark disputes and legal misunderstandings, avoid the
following phrases, CLI parameter names, and repository titles across
project materials:

#### Prohibited Trademark and Brand Phrases

-   Avoid implying official endorsement: **\"Official SonarQube
    > Integration\"**, **\"Certified Sonar Plugin\"**, **\"Approved by
    > SonarSource\"**, or **\"Official JaCoCo Extension\"**^12^.

-   Avoid standalone trademark subcommands: Avoid **mytool sonar**,
    > **mytool jacoco**, or **mytool sonarqube**^12^.

-   Avoid using trademarks as mode parameters: Avoid **\--mode=sonar**,
    > **\--sonar**, or **\--engine=sonar**^12^. Use descriptive options
    > such as \--format=sonar-xml or \--compatibility=sonarqube
    > instead^12^.

-   Avoid trademark terms in project handles: Avoid repository names
    > like **github.com/user/sonar-analyzer** or Java package names like
    > **com.example.sonar.cli**^12^.

#### Prohibited License and Copyleft Statements

-   Avoid misstating LGPL boundary rules: Do not state **\"This tool is
    > governed by LGPL because it executes Descartes\"** or
    > **\"Descartes requires our CLI source code to be LGPL.\"**\
    > \[cite: 5, 6\]

-   Avoid incorrect license claims for Category B binaries: Do not state
    > **\"JaCoCo is re-licensed under Apache-2.0.\"** (JaCoCo components
    > remain EPL-2.0 inside binary packages)^1^.

-   Avoid ambiguous dual-license statements: Do not state **\"JavaParser
    > is included under LGPL\"** without noting the election of
    > Apache-2.0^7^.

## 6. Conclusions and Operational Recommendations

Developing an Apache-2.0 Java CLI tool that integrates with JaCoCo,
Descartes, and JavaParser is fully achievable under open-source licenses
and foundation distribution policies when structured correctly:

1.  **JaCoCo**: In-process execution via Maven dependencies and binary
    > packaging of jacocoagent.jar in release ZIPs are compatible with
    > an Apache-2.0 distribution^1^. Compliance requires adhering to ASF
    > Category B rules: exclude Category B binaries from source
    > releases, add attribution to the NOTICE file, label JaCoCo in the
    > README, and provide a source availability notice for JaCoCo^1^.

2.  **Descartes**: Execute Descartes strictly as an external
    > subprocess^5^. Do not bundle pitest-descartes binaries in release
    > packages to prevent Category X violations under ASF policy and
    > LGPL copyleft attachment^2^.

3.  **JavaParser**: Formally elect the Apache License 2.0 option for
    > JavaParser and document this election in the project\'s NOTICE
    > file^7^.

4.  **Trademarks**: Use third-party brand names in documentation
    > strictly for descriptive, nominative fair use^12^. Replace
    > standalone CLI mode names (e.g., \--mode=sonar) with descriptive
    > parameter formats (e.g., \--format=sonar-xml) to comply with
    > SonarSource trademark policies^12^.

#### Alıntılanan çalışmalar

1.  AI Eclipse Public License - Oracle Help Center,
    > [[https://docs.oracle.com/en/industries/communications/network-data-analytics/24.3.0/nwdaf-lium-24-3-0/eclipse-public-license.html]{.underline}](https://docs.oracle.com/en/industries/communications/network-data-analytics/24.3.0/nwdaf-lium-24-3-0/eclipse-public-license.html)

2.  ASF 3rd Party License Policy \| Apache Software Foundation,
    > [[https://www.apache.org/legal/resolved.html]{.underline}](https://www.apache.org/legal/resolved.html)

3.  Eclipse Public License 2.0 \| Software Package Data Exchange (SPDX),
    > [[https://spdx.org/licenses/EPL-2.0.html]{.underline}](https://spdx.org/licenses/EPL-2.0.html)

4.  Third Party Licence Notes - VWgroupsupply.com,
    > [[https://www.vwgroupsupply.com/one-kbp-pub/en/kbp_public/rechtliches_4/lizenzhinweise_dritter/license_documentation.html]{.underline}](https://www.vwgroupsupply.com/one-kbp-pub/en/kbp_public/rechtliches_4/lizenzhinweise_dritter/license_documentation.html)

5.  Pipes and FIFOs (The GNU C Library),
    > [[https://doc.guix.gnu.org/libc/2.41/en/html_node/Pipes-and-FIFOs.html]{.underline}](https://doc.guix.gnu.org/libc/2.41/en/html_node/Pipes-and-FIFOs.html)

6.  The GNU C Library - Pipes and FIFOs,
    > [[https://ftp.gnu.org/old-gnu/Manuals/glibc-2.2.3/html_chapter/libc_15.html]{.underline}](https://ftp.gnu.org/old-gnu/Manuals/glibc-2.2.3/html_chapter/libc_15.html)

7.  Third-Party License Acknowledgments - Broadcom TechDocs,
    > [[https://techdocs.broadcom.com/content/dam/broadcom/techdocs/us/en/assets/enterprise-software/layer7-privileged-access-manager/symantec-privileged-access-manager-4.0.1-TPSRs.pdf]{.underline}](https://techdocs.broadcom.com/content/dam/broadcom/techdocs/us/en/assets/enterprise-software/layer7-privileged-access-manager/symantec-privileged-access-manager-4.0.1-TPSRs.pdf)

8.  Licensing Information User Manual Graal Development Kit for
    > Micronaut 4.9.1,
    > [[https://graal.cloud/gdk/about/lium/]{.underline}](https://graal.cloud/gdk/about/lium/)

9.  Third Party Notices \| Meta Quest,
    > [[https://www.meta.com/legal/quest/third-party-notices/22/]{.underline}](https://www.meta.com/legal/quest/third-party-notices/22/)

10. Third-Party Notices and/or Licenses - Oracle Help Center,
    > [[https://docs.oracle.com/en/industries/communications/cloud-native-core/2.3.4/cnc_licensing/third-party-notices-and-or-licenses1.html]{.underline}](https://docs.oracle.com/en/industries/communications/cloud-native-core/2.3.4/cnc_licensing/third-party-notices-and-or-licenses1.html)

11. gauge-java/notice.md at master · getgauge/gauge-java · GitHub,
    > [[https://github.com/getgauge/gauge-java/blob/master/notice.md]{.underline}](https://github.com/getgauge/gauge-java/blob/master/notice.md)

12. Sonar Trademark Use & Guidelines,
    > [[https://www.sonarsource.com/trademark-use/]{.underline}](https://www.sonarsource.com/trademark-use/)

13. Legal Documents \| Strategic Value-Added Reseller Agreement - Sonar,
    > [[https://www.sonarsource.com/legal/strategic-value-added-reseller-agreement/]{.underline}](https://www.sonarsource.com/legal/strategic-value-added-reseller-agreement/)
