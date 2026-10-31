package gog.my_project.data_base.migration.ast.interfaces

import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

interface IMigrationAst<MD : IMigrationAstMetadata> {
    var metadata: MD
}