package org.sonatype.nexus.plugins.ruby;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.maven.index.artifact.Gav;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelBuildingException;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.nexus.plugins.mavenbridge.NexusMavenBridge;
import org.sonatype.nexus.proxy.LocalStorageException;
import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.item.StorageFileItem;
import org.sonatype.nexus.proxy.maven.MavenRepository;
import org.sonatype.nexus.proxy.maven.MetadataLocator;
import org.sonatype.nexus.proxy.storage.local.fs.DefaultFSLocalRepositoryStorage;

import de.saumya.mojo.gems.ArtifactCoordinates;
import de.saumya.mojo.gems.MavenArtifact;

@Component( role = RubyRepositoryHelper.class )
public class DefaultRubyRepositoryHelper
    implements RubyRepositoryHelper
{
    @Requirement
    private MetadataLocator metadataLocator;

    @Requirement
    private NexusMavenBridge bridge;

    public MetadataLocator getMetadataLocator()
    {
        return metadataLocator;
    }

    public MavenArtifact getMavenArtifactForItem( MavenRepository masterRepository, StorageFileItem item )
        throws LocalStorageException
    {
        // TODO: this is here for simplicity only, jar's only for now
        if ( !item.getName().endsWith( ".pom" ) && !item.getName().endsWith( ".jar" ) )
        {
            return null;
        }

        // this works only on FS storages
        if ( !( masterRepository.getLocalStorage() instanceof DefaultFSLocalRepositoryStorage ) )
        {
            return null;
        }

        Gav gav = null;

        // item.getPath is either the pom or the jar
        // use the pom filename for GAV
        gav = masterRepository.getGavCalculator().pathToGav( item.getPath().replace( ".jar", ".pom" ) );

        // if the path does not represent a valid layouted artifact, skip it
        if ( gav == null )
        {
            return null;
        }

        try
        {
            // get both possible files: jar-file as well pom-files
            File pomFile =
                ( (DefaultFSLocalRepositoryStorage) masterRepository.getLocalStorage() ).getFileFromBase(
                    masterRepository, new ResourceStoreRequest( item.getPath().replace( ".jar", ".pom" ) ) );

            File jarFile =
                ( (DefaultFSLocalRepositoryStorage) masterRepository.getLocalStorage() ).getFileFromBase(
                    masterRepository, new ResourceStoreRequest( item.getPath().replace( ".pom", ".jar" ) ) );

            // pom must exists, jar don't have to
            if ( !pomFile.isFile() )
            {
                return null;
            }

            if ( !jarFile.exists() )
            {
                jarFile = null;
            }

            Model model = null;

            try
            {
                model = bridge.buildModel( new FileModelSource( pomFile ), Arrays.asList( masterRepository ) );
            }
            catch ( ModelBuildingException e )
            {
                // skip gem generation of this artifact
                return null;
            }

            ArtifactCoordinates coords =
                new ArtifactCoordinates( gav.getGroupId(), gav.getArtifactId(), gav.getVersion(), gav.getExtension() );

            // we have a jar artifact but no jarfile, so skip this one
            if ( "jar".equals( model.getPackaging() ) && jarFile == null )
            {
                return null;
            }

            return new MavenArtifact( model, coords, jarFile );
        }
        catch ( IOException e )
        {
            throw new LocalStorageException( "We got IOException while retrieving POM file for \""
                + item.getRepositoryItemUid() + "\"!", e );
        }
    }

    public File getMavenRepositoryBasedir( MavenRepository mavenRepository )
        throws LocalStorageException
    {
        if ( mavenRepository.getLocalStorage() instanceof DefaultFSLocalRepositoryStorage )
        {
            File result =
                ( (DefaultFSLocalRepositoryStorage) mavenRepository.getLocalStorage() ).getBaseDir( mavenRepository,
                    new ResourceStoreRequest( "/" ) );

            return result;
        }
        else
        {
            return null;
        }
    }

}
